package br.com.coe.servicos.contrato;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.notificacao.Avisos;
import br.com.coe.servicos.profissional.ProfissionalSuspenso;

/**
 * RN44f (11/11): ao suspender o profissional, as diárias pagas e ainda não iniciadas voltam inteiras ao cliente pelo
 * livro-caixa ({@link ReembolsoDeDiaria}), com aviso; em andamento ou aguardando seguem o fluxo; contestada segue a
 * disputa. Na transação da suspensão, contrato por contrato e diárias por data (a ordem das travas de sempre).
 */
@Component
class ReembolsoNaSuspensao {

    private final NamedParameterJdbcTemplate jdbc;
    private final ReembolsoDeDiaria reembolso;
    private final Avisos avisos;
    private final Clock clock;

    ReembolsoNaSuspensao(NamedParameterJdbcTemplate jdbc, ReembolsoDeDiaria reembolso, Avisos avisos, Clock clock) {
        this.jdbc = jdbc;
        this.reembolso = reembolso;
        this.avisos = avisos;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoSuspender(ProfissionalSuspenso suspensao) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        expirarOsQueEsperamPagamento(suspensao.profissional(), agora);
        List<UUID> contratos =
                jdbc.queryForList("""
                SELECT DISTINCT contrato_id FROM diaria
                 WHERE profissional_id = :profissional AND status = 'paga'
                 ORDER BY contrato_id
                """, new MapSqlParameterSource("profissional", suspensao.profissional()), UUID.class);
        for (UUID contrato : contratos) {
            jdbc.queryForList(
                    "SELECT id FROM contrato WHERE id = :id FOR UPDATE",
                    new MapSqlParameterSource("id", contrato),
                    UUID.class);
            List<UUID> pagas = jdbc.queryForList("""
                    SELECT id FROM diaria WHERE contrato_id = :contrato AND status = 'paga'
                     ORDER BY data FOR UPDATE
                    """, new MapSqlParameterSource("contrato", contrato), UUID.class);
            for (UUID diaria : pagas) {
                reembolso.reembolsar(
                        contrato,
                        diaria,
                        StatusDiaria.PAGA,
                        ReembolsoDeDiaria.Motivo.OUTRO,
                        suspensao.admin(),
                        "ADMIN",
                        agora);
                avisos.avisarSobreDiaria(diaria, Avisos.Para.CLIENTE, Avisos.Aviso.DIARIA_REEMBOLSADA_SUSPENSAO);
            }
        }
    }

    /**
     * Pedido ainda esperando pagamento não pode ser pago com o profissional suspenso: expira agora, como na expiração
     * do pagamento (RN32). Pagamento que chegar depois cai no dinheiro sem pedido e volta inteiro (RN34c).
     */
    private void expirarOsQueEsperamPagamento(UUID profissional, OffsetDateTime agora) {
        List<UUID> contratos =
                jdbc.queryForList("""
                SELECT id FROM contrato WHERE profissional_id = :profissional AND status = 'aguardando_pagamento'
                 ORDER BY id FOR UPDATE
                """, new MapSqlParameterSource("profissional", profissional), UUID.class);
        if (contratos.isEmpty()) {
            return;
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("contratos", contratos)
                .addValue("motivo", "profissional-suspenso")
                .addValue("agora", agora);
        jdbc.update("""
                UPDATE contrato SET status = 'expirado', motivo_cancelamento = :motivo, atualizado_em = :agora
                 WHERE id IN (:contratos) AND status = 'aguardando_pagamento'
                """, parametros);
        jdbc.update(
                "UPDATE cobranca SET status = 'expirada' WHERE contrato_id IN (:contratos) AND status = 'pendente'",
                parametros);
        jdbc.update("""
                WITH canceladas AS (
                    UPDATE diaria SET status = 'cancelada', cancelada_em = :agora, atualizado_em = :agora
                     WHERE contrato_id IN (:contratos) AND status = 'agendada'
                    RETURNING id
                )
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, motivo, criado_em)
                SELECT id, 'agendada', 'cancelada', 'sistema', :motivo, :agora FROM canceladas
                """, parametros);
    }
}
