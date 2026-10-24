package br.com.coe.servicos.contrato;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Contrato não pago no prazo ({@code pagamento_expira_em}) vira {@code expirado} e as diárias agendadas viram
 * {@code cancelada}, liberando os dias na agenda (RN32). Idempotente: só pega quem ainda aguarda pagamento, com
 * {@code FOR UPDATE SKIP LOCKED}, então várias instâncias rodam juntas sem ShedLock (a ShedLock entra no DOM-08,
 * com a liberação automática). Nenhum dinheiro se move: contrato aguardando pagamento ainda não tem pagamento.
 */
@Component
public class ExpiracaoDeContratos {

    static final String MOTIVO = "pagamento-expirado";

    private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoDeContratos.class);
    private static final int LOTE = 200;
    private static final int MAXIMO_DE_LOTES = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ExpiracaoDeContratos(NamedParameterJdbcTemplate jdbc, TransactionTemplate transacao, Clock clock) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${coe.contrato.expiracao:1m}", initialDelayString = "${coe.contrato.expiracao:1m}")
    public void executar() {
        int expirados = 0;
        for (int lote = 0; lote < MAXIMO_DE_LOTES; lote++) {
            Integer doLote = transacao.execute(status -> expirarLote());
            int quantos = doLote == null ? 0 : doLote;
            expirados += quantos;
            if (quantos < LOTE) {
                break;
            }
        }
        if (expirados > 0) {
            LOG.info("Contratos expirados sem pagamento: {}", expirados);
        }
    }

    private int expirarLote() {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        MapSqlParameterSource parametros = new MapSqlParameterSource("agora", agora).addValue("lote", LOTE);
        List<UUID> contratos = jdbc.queryForList("""
                SELECT id FROM contrato
                 WHERE status = 'aguardando_pagamento' AND pagamento_expira_em <= :agora
                 ORDER BY pagamento_expira_em
                 LIMIT :lote
                 FOR UPDATE SKIP LOCKED
                """, parametros, UUID.class);
        if (contratos.isEmpty()) {
            return 0;
        }
        parametros.addValue("contratos", contratos).addValue("motivo", MOTIVO);
        jdbc.update("""
                UPDATE contrato SET status = 'expirado', motivo_cancelamento = :motivo, atualizado_em = :agora
                 WHERE id IN (:contratos) AND status = 'aguardando_pagamento'
                """, parametros);
        // a troca de status e o histórico na mesma instrução
        jdbc.update("""
                WITH canceladas AS (
                    UPDATE diaria SET status = 'cancelada', cancelada_em = :agora, atualizado_em = :agora
                     WHERE contrato_id IN (:contratos) AND status = 'agendada'
                    RETURNING id
                )
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, motivo, criado_em)
                SELECT id, 'agendada', 'cancelada', 'sistema', :motivo, :agora FROM canceladas
                """, parametros);
        return contratos.size();
    }
}
