package br.com.coe.servicos.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Service;

import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;

/**
 * Parâmetros de negócio em vigor (CORE-12), lidos da tabela {@code configuracao} e já tipados.
 * Nunca fixe esses valores no código.
 *
 * <ul>
 *   <li>Na subida, carrega e valida todas as chaves: se faltar uma ou houver valor inválido, a
 *       aplicação não sobe e diz qual (fail fast).
 *   <li>Depois, os valores ficam guardados por 60 s (contados pelo {@link Clock}). Se a releitura
 *       falhar, mantém os últimos válidos, registra o erro e só tenta de novo no próximo prazo.
 *   <li>Para um cálculo que usa mais de um parâmetro (ex.: comissão e quem paga), use
 *       {@link #parametros()}: todos vêm do mesmo snapshot.
 *   <li>{@link #invalidarCache()} força a releitura, por exemplo depois de o admin mudar um parâmetro.
 * </ul>
 */
@Service
@DependsOnDatabaseInitialization
public class ConfiguracaoNegocio {

    static final Duration VALIDADE_DO_CACHE = Duration.ofSeconds(60);

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoNegocio.class);
    private static final int LIMITE_LC150_MAXIMO = 2;

    /** Os parâmetros em vigor num instante, todos de uma mesma leitura. */
    public record Parametros(
            Percentual comissao,
            TaxaPagaPor taxaPagaPor,
            Duration autoLiberacao,
            Duration prazoDisputa,
            int limiteDiaristaJanela7Dias,
            int tentativasAntesAnalise,
            Duration meiaDiariaMaxima,
            Duration expiracaoPagamento,
            String versaoTermos) {}

    private record Snapshot(Parametros parametros, Instant lidoEm, long geracao) {}

    private final FonteConfiguracao fonte;
    private final Clock clock;
    private final Object trava = new Object();
    private final AtomicLong geracao = new AtomicLong();
    private volatile Snapshot atual;

    public ConfiguracaoNegocio(FonteConfiguracao fonte, Clock clock) {
        this.fonte = fonte;
        this.clock = clock;
        this.atual = new Snapshot(carregar(), clock.instant(), geracao.get());
    }

    /** Todos os parâmetros de uma mesma leitura. Use quando o cálculo depende de mais de um. */
    public Parametros parametros() {
        Snapshot snapshot = atual;
        if (valido(snapshot, clock.instant())) {
            return snapshot.parametros();
        }
        synchronized (trava) {
            Instant agora = clock.instant();
            snapshot = atual;
            if (valido(snapshot, agora)) {
                return snapshot.parametros();
            }
            long geracaoDaLeitura = geracao.get();
            try {
                atual = new Snapshot(carregar(), agora, geracaoDaLeitura);
            } catch (RuntimeException erro) {
                LOG.error(
                        "Não foi possível reler a configuração de negócio; mantendo os últimos valores válidos", erro);
                atual = new Snapshot(snapshot.parametros(), agora, geracaoDaLeitura);
            }
            return atual.parametros();
        }
    }

    /** Comissão da COE sobre cada diária (RN31). */
    public Percentual comissao() {
        return parametros().comissao();
    }

    /** Quem paga a comissão (PA05: o cliente). */
    public TaxaPagaPor taxaPagaPor() {
        return parametros().taxaPagaPor();
    }

    /** Tempo sem resposta do cliente, a partir do "Terminei o dia", até liberar sozinha (RN38). */
    public Duration autoLiberacao() {
        return parametros().autoLiberacao();
    }

    /** Prazo da equipe para decidir uma disputa (RN44). */
    public Duration prazoDisputa() {
        return parametros().prazoDisputa();
    }

    /** Máximo de diárias da mesma diarista com o mesmo cliente em qualquer janela de 7 dias (RN51). */
    public int limiteDiaristaJanela7Dias() {
        return parametros().limiteDiaristaJanela7Dias();
    }

    /** Tentativas de passar contato antes de a conta ir para análise (RN49). */
    public int tentativasAntesAnalise() {
        return parametros().tentativasAntesAnalise();
    }

    /** Duração máxima da meia diária (PA10). */
    public Duration meiaDiariaMaxima() {
        return parametros().meiaDiariaMaxima();
    }

    /** Tempo para pagar antes de o pedido expirar (RN32). */
    public Duration expiracaoPagamento() {
        return parametros().expiracaoPagamento();
    }

    /** Versão vigente dos termos de uso e privacidade (RNF18). */
    public String versaoTermos() {
        return parametros().versaoTermos();
    }

    /** Descarta os valores guardados: a próxima leitura vai ao banco, mesmo que uma leitura esteja em curso. */
    public void invalidarCache() {
        geracao.incrementAndGet();
    }

    /** Válido se for da geração atual e lido há menos de 60 s (relógio que voltou no tempo também invalida). */
    private boolean valido(Snapshot snapshot, Instant agora) {
        return snapshot.geracao() == geracao.get()
                && !agora.isBefore(snapshot.lidoEm())
                && agora.isBefore(snapshot.lidoEm().plus(VALIDADE_DO_CACHE));
    }

    private Parametros carregar() {
        Map<String, String> brutos = fonte.lerVigentes();
        return new Parametros(
                ler(brutos, ChaveConfiguracao.COMISSAO, Percentual::de),
                ler(brutos, ChaveConfiguracao.TAXA_PAGA_POR, TaxaPagaPor::deCodigo),
                ler(brutos, ChaveConfiguracao.AUTO_LIBERA_HORAS, texto -> Duration.ofHours(positivo(texto))),
                ler(brutos, ChaveConfiguracao.PRAZO_DISPUTA_HORAS, texto -> Duration.ofHours(positivo(texto))),
                ler(brutos, ChaveConfiguracao.LIMITE_DOMESTICO_SEMANA, ConfiguracaoNegocio::limiteLc150),
                ler(brutos, ChaveConfiguracao.TENTATIVAS_ANTES_ANALISE, ConfiguracaoNegocio::positivo),
                ler(brutos, ChaveConfiguracao.MEIA_DIARIA_HORAS, texto -> Duration.ofHours(positivo(texto))),
                ler(brutos, ChaveConfiguracao.PAGAMENTO_EXPIRA_MINUTOS, texto -> Duration.ofMinutes(positivo(texto))),
                ler(brutos, ChaveConfiguracao.VERSAO_TERMOS, ConfiguracaoNegocio::naoVazio));
    }

    private static int positivo(String texto) {
        int valor = Integer.parseInt(texto);
        if (valor <= 0) {
            throw new IllegalArgumentException("precisa ser maior que zero");
        }
        return valor;
    }

    private static int limiteLc150(String texto) {
        int valor = positivo(texto);
        if (valor > LIMITE_LC150_MAXIMO) {
            throw new IllegalArgumentException("não pode passar de " + LIMITE_LC150_MAXIMO + " (RN52)");
        }
        return valor;
    }

    private static String naoVazio(String texto) {
        if (texto.isBlank()) {
            throw new IllegalArgumentException("não pode ser vazio");
        }
        return texto;
    }

    private static <T> T ler(Map<String, String> brutos, ChaveConfiguracao chave, Function<String, T> conversor) {
        String texto = brutos.get(chave.name());
        if (texto == null) {
            throw new IllegalStateException(
                    "Configuração de negócio incompleta: falta a chave " + chave.name() + " na tabela configuracao");
        }
        try {
            return conversor.apply(texto);
        } catch (RuntimeException erro) {
            throw new IllegalStateException(
                    "Configuração de negócio inválida: chave " + chave.name() + " = '" + texto + "' ("
                            + erro.getMessage() + ")",
                    erro);
        }
    }
}
