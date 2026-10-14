package br.com.coe.servicos.compartilhado.limite;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import io.github.bucket4j.postgresql.PostgreSQLSelectForUpdateBasedProxyManager;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;

/**
 * Limite de taxa dos endpoints sensíveis (CORE-07), com os baldes no Postgres (tabela limite_balde,
 * V16): vale com várias instâncias. Os baldes são cobrados na ordem dada e o primeiro recusado para
 * tudo (IP antes do global: quem passa do limite do IP não gasta o global). Global = uma de N fatias
 * sorteada (H1). Falha do banco nunca deixa passar sem contar (fail-closed): trava disputada além do
 * lock_timeout = 429 com espera curta; banco ou pool fora = 503.
 */
public class LimitadorDeTaxa implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LimitadorDeTaxa.class);
    private static final String MENSAGEM = "Muitas tentativas. Aguarde um pouco e tente de novo.";
    private static final String TRAVA_INDISPONIVEL = "55P03";
    private static final Duration SOBRA_DEPOIS_DE_CHEIO = Duration.ofSeconds(10);

    private final DataSource pool;
    private final PostgreSQLSelectForUpdateBasedProxyManager<String> baldes;
    private final PropriedadesDeLimites propriedades;
    private final Map<String, BucketConfiguration> configuracoes = new ConcurrentHashMap<>();

    public LimitadorDeTaxa(DataSource pool, PropriedadesDeLimites propriedades, Clock clock) {
        this.pool = pool;
        this.propriedades = propriedades;
        this.baldes = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(
                        new ConexoesComLockTimeout(pool, propriedades.lockTimeout()))
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("limite_balde")
                .idColumn("chave")
                .stateColumn("estado")
                .expiresAtColumn("expira_em")
                .clientClock(new RelogioDoBucket4j(clock))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(SOBRA_DEPOIS_DE_CHEIO))
                .build();
    }

    /** Cobra um pedido de cada balde, na ordem; o primeiro sem saldo lança 429 com a espera. */
    public void consumir(Balde... baldesACobrar) {
        for (Balde balde : baldesACobrar) {
            cobrar(balde);
        }
    }

    private void cobrar(Balde balde) {
        PropriedadesDeLimites.Regra regra = propriedades.regra(balde.regra());
        String chave;
        long capacidade;
        if (balde.global()) {
            chave = balde.regra() + ":global:" + ThreadLocalRandom.current().nextInt(regra.fatias());
            capacidade = regra.capacidadePorFatia();
        } else {
            chave = balde.regra() + ":" + balde.chave();
            capacidade = regra.capacidade();
        }
        BucketConfiguration configuracao = configuracoes.computeIfAbsent(
                balde.regra() + "/" + capacidade, ignorada -> configuracao(capacidade, regra.janela()));
        ConsumptionProbe resultado;
        try {
            resultado = baldes.builder().build(chave, () -> configuracao).tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException falha) {
            throw traduzir(balde.regra(), falha);
        }
        if (!resultado.isConsumed()) {
            LOG.info("Limite atingido: {}", balde.regra());
            throw new MuitasTentativasException(MENSAGEM, Duration.ofNanos(resultado.getNanosToWaitForRefill()));
        }
    }

    private RuntimeException traduzir(String regra, RuntimeException falha) {
        for (Throwable causa = falha; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && TRAVA_INDISPONIVEL.equals(sql.getSQLState())) {
                LOG.warn("Balde disputado além do lock_timeout: {}", regra);
                return new MuitasTentativasException(MENSAGEM, propriedades.esperaNaDisputa());
            }
        }
        LOG.error(
                "Limitador de taxa indisponível ({}): {}",
                regra,
                falha.getClass().getSimpleName());
        return new ServicoInstavelException("Limitador de taxa indisponível", falha);
    }

    private static BucketConfiguration configuracao(long capacidade, Duration janela) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacidade)
                        .refillIntervally(capacidade, janela)
                        .build())
                .build();
    }

    /** Apaga até {@code lote} baldes vencidos (SKIP LOCKED: várias instâncias podem rodar juntas). */
    int removerVencidos(int lote) {
        return baldes.removeExpired(lote);
    }

    /** Só para o teste: o login com que o pool do limitador conecta (o mesmo da aplicação). */
    String usuarioConectado() throws java.sql.SQLException {
        try (java.sql.Connection conexao = pool.getConnection();
                java.sql.Statement consulta = conexao.createStatement();
                java.sql.ResultSet resultado = consulta.executeQuery("SELECT current_user")) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    @Override
    public void close() throws Exception {
        if (pool instanceof AutoCloseable fechavel) {
            fechavel.close();
        }
    }
}
