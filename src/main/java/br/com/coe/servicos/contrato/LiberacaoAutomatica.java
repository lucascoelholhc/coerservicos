package br.com.coe.servicos.contrato;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Duration ESPERA_DEPOIS_DE_FALHA = Duration.ofHours(1);

    /** Diárias que falharam nesta instância e quando: ficam de fora por uma hora. */
    private final Map<UUID, Instant> falhas = new ConcurrentHashMap<>();

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
        Instant agora = clock.instant();
        falhas.values().removeIf(quando -> quando.plus(ESPERA_DEPOIS_DE_FALHA).isBefore(agora));
        List<UUID> vencidas = jdbc.queryForList(
                """
                SELECT id FROM diaria
                 WHERE status = 'aguardando' AND auto_libera_em <= :agora
                 ORDER BY auto_libera_em
                 LIMIT :lote
                """,
                new MapSqlParameterSource("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                        .addValue("lote", LOTE + falhas.size()),
                UUID.class);
        int liberadas = 0;
        for (UUID diaria : vencidas) {
            if (falhas.containsKey(diaria)) {
                continue; // falhou há pouco: não ocupa a vaga das outras (volta depois da espera)
            }
            try {
                if (liberarUma(diaria)) {
                    liberadas++;
                }
            } catch (RuntimeException erro) {
                // uma diária com problema não segura as outras; um aviso só, e a equipe resolve
                falhas.put(diaria, agora);
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

    /** Uma diária, na própria transação, sem engolir o erro (o job e os testes de concorrência usam). */
    boolean liberarUma(UUID diaria) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return Boolean.TRUE.equals(transacao.execute(t -> liberarSeVenceu(diaria, agora)));
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
