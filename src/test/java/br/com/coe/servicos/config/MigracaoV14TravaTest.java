package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import br.com.coe.servicos.IntegracaoTest;

/**
 * A V14 apaga a token_senha e muda o codigo_sms: se alguma das duas tiver linhas, ela para com
 * erro em vez de perder dado. Prova num banco à parte (no mesmo Postgres do Testcontainers),
 * migrado até a V13, com uma linha inserida antes de aplicar a V14.
 */
class MigracaoV14TravaTest extends IntegracaoTest {

    @Autowired
    JdbcConnectionDetails conexao;

    @Autowired
    JdbcTemplate jdbc;

    private DriverManagerDataSource bancoNovo(String nome) {
        jdbc.execute("DROP DATABASE IF EXISTS " + nome);
        jdbc.execute("CREATE DATABASE " + nome);
        String url = conexao.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + nome + "$1");
        return new DriverManagerDataSource(url, conexao.getUsername(), conexao.getPassword());
    }

    private static Flyway flyway(DriverManagerDataSource banco, String alvo) {
        return Flyway.configure()
                .dataSource(banco)
                .locations("classpath:db/migration")
                .target(alvo)
                .load();
    }

    private void comBanco(String nome, java.util.function.Consumer<DriverManagerDataSource> teste) {
        DriverManagerDataSource banco = bancoNovo(nome);
        try {
            teste.accept(banco);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + nome + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("codigo_sms com linha: a V14 para com erro")
    void codigoSmsComLinha() {
        comBanco("coe_trava_codigo", banco -> {
            flyway(banco, "13").migrate();
            new JdbcTemplate(banco).update("""
                    INSERT INTO codigo_sms (celular, finalidade, codigo_hash, expira_em)
                    VALUES ('47900000501', 'login', 'hash', now() + interval '5 minutes')""");

            assertThatThrownBy(() -> flyway(banco, "14").migrate()).hasStackTraceContaining("codigo_sms tem linhas");
        });
    }

    @Test
    @DisplayName("token_senha com linha: a V14 para com erro")
    void tokenSenhaComLinha() {
        comBanco("coe_trava_token", banco -> {
            flyway(banco, "13").migrate();
            JdbcTemplate sql = new JdbcTemplate(banco);
            UUID usuario = sql.queryForObject("""
                    INSERT INTO usuario (nome, celular, email, senha_hash)
                    VALUES ('Trava', '47900000502', 'trava@teste.coe.local', 'hash') RETURNING id""", UUID.class);
            sql.update(
                    "INSERT INTO token_senha (usuario_id, token_hash, expira_em) VALUES (?, 'hash', now() + interval '1 hour')",
                    usuario);

            assertThatThrownBy(() -> flyway(banco, "14").migrate()).hasStackTraceContaining("token_senha tem linhas");
        });
    }
}
