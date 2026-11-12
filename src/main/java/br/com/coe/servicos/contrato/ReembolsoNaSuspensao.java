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
}
