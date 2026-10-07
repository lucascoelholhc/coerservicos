package br.com.coe.servicos.compartilhado.auditoria;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Grava em log_auditoria (só inserção). Quem chama manda os dados já mascarados: nada de CPF, Pix,
 * endereço, celular ou e-mail inteiros em {@code antes}/{@code depois}. Data do Clock.
 */
@Component
public class RegistroDeAuditoria {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    RegistroDeAuditoria(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** Ação do sistema (sem ator), na transação de quem chama. */
    public void registrarDoSistema(
            String acao, String entidade, UUID entidadeId, Map<String, ?> antes, Map<String, ?> depois, String ip) {
        jdbc.update(
                "INSERT INTO log_auditoria (ator_papel, acao, entidade, entidade_id, antes, depois, ip, criado_em)"
                        + " VALUES ('SISTEMA', ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS inet), ?)",
                acao,
                entidade,
                entidadeId,
                json.writeValueAsString(antes),
                json.writeValueAsString(depois),
                ip,
                Timestamp.from(clock.instant()));
    }
}
