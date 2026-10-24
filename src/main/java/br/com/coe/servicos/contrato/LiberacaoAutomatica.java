package br.com.coe.servicos.contrato;

import java.sql.Timestamp;
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

/**
 * Liberação automática (RN38, PA08): diária aguardando com {@code auto_libera_em} vencido (12 h, configurável, desde o
 * "Terminei o dia") é liberada pelo sistema, uma por transação, com as travas na ordem de sempre (contrato, depois a
 * diária) e o status conferido de novo dentro da trava: se o cliente aprovou antes, nada acontece (RN39). Roda numa
 * instância por vez (ShedLock); a idempotência também está no banco.
 */
@Component
public class LiberacaoAutomatica {

    private static final Logger LOG = LoggerFactory.getLogger(LiberacaoAutomatica.class);
    private static final int LOTE = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final LiberacaoDeDiaria liberacao;
    private final Clock clock;

    LiberacaoAutomatica(
            NamedParameterJdbcTemplate jdbc, TransactionTemplate transacao, LiberacaoDeDiaria liberacao, Clock clock) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.liberacao = liberacao;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${coe.contrato.liberacao:1m}", initialDelayString = "${coe.contrato.liberacao:1m}")
    @SchedulerLock(name = "liberacao-automatica", lockAtMostFor = "PT10M")
    public void executar() {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UUID> vencidas =
                jdbc.queryForList("""
                SELECT id FROM diaria
                 WHERE status = 'aguardando' AND auto_libera_em <= :agora
                 ORDER BY auto_libera_em
                 LIMIT :lote
                """, new MapSqlParameterSource("agora", agora).addValue("lote", LOTE), UUID.class);
        int liberadas = 0;
        for (UUID diaria : vencidas) {
            try {
                if (Boolean.TRUE.equals(transacao.execute(t -> liberarSeVenceu(diaria, agora)))) {
                    liberadas++;
                }
            } catch (RuntimeException erro) {
                // uma diária com problema não segura as outras; fica para a próxima rodada e para a equipe
                LOG.error(
                        "Falha ao liberar sozinha a diária {}: {}",
                        diaria,
                        erro.getClass().getSimpleName(),
                        erro);
            }
        }
        if (liberadas > 0) {
            LOG.info("Diárias liberadas sozinhas depois do prazo: {}", liberadas);
        }
    }

    private boolean liberarSeVenceu(UUID diaria, OffsetDateTime agora) {
        UUID contrato = jdbc.queryForObject(
                "SELECT contrato_id FROM diaria WHERE id = :id", new MapSqlParameterSource("id", diaria), UUID.class);
        jdbc.queryForList(
                "SELECT id FROM contrato WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", contrato),
                UUID.class);
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT status, auto_libera_em FROM diaria WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", diaria));
        Timestamp prazo = (Timestamp) linha.get("auto_libera_em");
        if (!"aguardando".equals(linha.get("status"))
                || prazo == null
                || prazo.toInstant().isAfter(agora.toInstant())) {
            return false; // o cliente aprovou (ou reclamou) antes: nada a fazer
        }
        liberacao.liberar(contrato, diaria, LiberacaoDeDiaria.Por.AUTOMATICA, null, agora);
        return true;
    }
}
