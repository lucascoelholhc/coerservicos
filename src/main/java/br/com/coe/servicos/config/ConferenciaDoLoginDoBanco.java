package br.com.coe.servicos.config;

import java.util.List;
import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prod: a aplicação só sobe se o login do datasource for o login da aplicação, membro do coe_app e sem
 * poder a mais. Recusa superusuário, dono do schema e dono de qualquer tabela do schema (também por
 * herança de papel), e login fora do coe_app: com um desses, os gatilhos e REVOKEs que protegem o ledger
 * e a auditoria deixariam de valer. A mensagem leva o nome do login, nunca a senha. Ligada só no
 * application-prod.yml ({@code coe.seguranca.conferir-login-do-banco}).
 */
@Component
@ConditionalOnProperty(name = "coe.seguranca.conferir-login-do-banco", havingValue = "true")
class ConferenciaDoLoginDoBanco implements InitializingBean {

    private static final String PAPEL_DA_APLICACAO = "coe_app";
    private static final int TABELAS_NA_MENSAGEM = 5;

    private final JdbcTemplate jdbc;

    ConferenciaDoLoginDoBanco(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() {
        String login = jdbc.queryForObject("SELECT current_user", String.class);
        if (Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class))) {
            throw recusa(login, "é superusuário");
        }
        // antes das checagens de dono: fora do coe_app o login nem enxerga o schema (current_schema() nulo)
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_has_role(current_user, ?, 'MEMBER')", Boolean.class, PAPEL_DA_APLICACAO))) {
            throw recusa(login, "não é membro do papel " + PAPEL_DA_APLICACAO);
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_has_role(nspowner, 'USAGE') FROM pg_namespace WHERE nspname = current_schema()",
                Boolean.class))) {
            throw recusa(login, "é dono do schema");
        }
        List<String> tabelas = jdbc.queryForList(
                "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname = current_schema() AND pg_has_role(c.relowner, 'USAGE')"
                        + " ORDER BY c.relname LIMIT " + TABELAS_NA_MENSAGEM,
                String.class);
        if (!tabelas.isEmpty()) {
            throw recusa(login, "é dono de objetos do schema (" + String.join(", ", tabelas) + ")");
        }
    }

    private static IllegalStateException recusa(String login, String motivo) {
        return new IllegalStateException("Login do banco recusado: '" + login + "' " + motivo
                + ". Use em DB_USER um login membro de " + PAPEL_DA_APLICACAO
                + " que não seja dono de nada (o dono do schema fica só no SPRING_FLYWAY_USER).");
    }
}
