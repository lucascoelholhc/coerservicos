package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import br.com.coe.servicos.compartilhado.cripto.Campo;
import br.com.coe.servicos.compartilhado.cripto.CifraDeCampo;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Envio dos repasses pendentes por Pix (PA02, RN34a), uma instância por vez (ShedLock). Só sai repasse de profissional
 * com a chave conferida pelo admin (RN15). O gateway é chamado fora de transação e de trava: antes de mandar, consulta
 * pela referência (id da diária), então uma queda entre o envio e a gravação não manda o Pix duas vezes. Depois grava,
 * travando a linha e conferindo de novo que ela está pendente. A chave em claro só existe durante a chamada.
 */
@Component
public class EnvioDeRepasses {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeRepasses.class);
    private static final int LOTE = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final GatewayPagamento gateway;
    private final CifraDeCampo cifra;
    private final Repasses repasses;
    private final Clock clock;

    EnvioDeRepasses(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transacao,
            GatewayPagamento gateway,
            CifraDeCampo cifra,
            Repasses repasses,
            Clock clock) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.gateway = gateway;
        this.cifra = cifra;
        this.repasses = repasses;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${coe.pagamento.envio-repasses:1m}",
            initialDelayString = "${coe.pagamento.envio-repasses:1m}")
    @SchedulerLock(name = "envio-de-repasses", lockAtMostFor = "PT10M")
    public void executar() {
        List<UUID> prontos = jdbc.queryForList("""
                SELECT r.id FROM repasse r JOIN profissional p ON p.id = r.profissional_id
                 WHERE r.status = 'pendente' AND p.pix_conferido_em IS NOT NULL
                 ORDER BY r.criado_em
                 LIMIT :lote
                """, new MapSqlParameterSource("lote", LOTE), UUID.class);
        for (UUID repasse : prontos) {
            try {
                enviarUm(repasse);
            } catch (RuntimeException erro) {
                // um repasse com problema não segura os outros; volta na próxima rodada
                LOG.error("Repasse {} não enviado: {}", repasse, erro.getClass().getSimpleName());
            }
        }
    }

    private void enviarUm(UUID repasse) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT r.valor, rd.diaria_id, p.chave_pix_tipo, p.chave_pix_cifrada
                  FROM repasse r
                  JOIN repasse_diaria rd ON rd.repasse_id = r.id
                  JOIN profissional p ON p.id = r.profissional_id
                 WHERE r.id = :id AND r.status = 'pendente' AND p.pix_conferido_em IS NOT NULL
                """, new MapSqlParameterSource("id", repasse));
        if (linhas.isEmpty()) {
            return;
        }
        Map<String, Object> r = linhas.get(0);
        String referencia = r.get("diaria_id").toString();
        String tipo = (String) r.get("chave_pix_tipo");
        byte[] chave = (byte[]) r.get("chave_pix_cifrada");
        GatewayPagamento.TransferenciaNoGateway transferencia = gateway.transferenciaPorReferencia(referencia)
                .filter(existente -> !existente.falhou())
                .orElseGet(() -> gateway.transferirPix(new GatewayPagamento.NovaTransferencia(
                        referencia,
                        new Dinheiro((BigDecimal) r.get("valor")),
                        GatewayPagamento.TipoChave.daCoe(tipo),
                        cifra.decifrar(chave, Campo.CHAVE_PIX),
                        "COE - repasse da diária")));
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        transacao.executeWithoutResult(status -> gravar(repasse, transferencia, tipo, chave, agora));
    }

    private void gravar(
            UUID repasse,
            GatewayPagamento.TransferenciaNoGateway transferencia,
            String tipo,
            byte[] chave,
            OffsetDateTime agora) {
        String situacao = jdbc.queryForObject(
                "SELECT status FROM repasse WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", repasse),
                String.class);
        if (!"pendente".equals(situacao)) {
            return; // outra rodada ou o webhook chegou antes
        }
        jdbc.update(
                """
                UPDATE repasse SET status = 'enviado', id_externo = :externo, enviado_em = :agora,
                                   tentativas = tentativas + 1, chave_pix_tipo = :tipo, chave_pix_cifrada = :chave,
                                   erro = NULL
                 WHERE id = :id
                """,
                new MapSqlParameterSource("id", repasse)
                        .addValue("externo", transferencia.idExterno())
                        .addValue("agora", agora)
                        .addValue("tipo", tipo)
                        .addValue("chave", chave));
        if (transferencia.concluida()) {
            repasses.confirmar(repasse, agora);
        } else if (transferencia.falhou()) {
            repasses.falhar(repasse, transferencia.motivoFalha());
        }
    }
}
