package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.coe.servicos.IntegracaoTest;

/**
 * Prod: a aplicação não sobe com um login de banco que pode mais do que o coe_app (superusuário, dono
 * do schema ou de alguma tabela, ou fora do coe_app). Cada caso num contexto Spring próprio, com o
 * DataSource daquele login.
 */
class ConferenciaDoLoginDoBancoTest extends IntegracaoTest {

    private static final String SENHA = "senha-so-do-teste";

    @Autowired
    private JdbcConnectionDetails banco;

    @AfterEach
    void apagarLogins() throws SQLException {
        executarComoDono(
                "DROP TABLE IF EXISTS tabela_do_login_dono",
                "DROP ROLE IF EXISTS login_app_ok",
                "DROP ROLE IF EXISTS login_sem_papel",
                "DROP ROLE IF EXISTS login_dono_de_tabela");
    }

    private ApplicationContextRunner contexto(String usuario, String senha, boolean ligada) {
        return new ApplicationContextRunner()
                .withBean(DataSource.class, () -> pool(usuario, senha))
                .withUserConfiguration(ConferenciaDoLoginDoBanco.class)
                .withPropertyValues("coe.seguranca.conferir-login-do-banco=" + ligada);
    }

    @Test
    @DisplayName("superusuário: não sobe, com mensagem clara e sem a senha")
    void superusuario() {
        contexto(banco.getUsername(), banco.getPassword(), true).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("superusuário")
                    .hasMessageContaining(banco.getUsername());
            // o Testcontainers usa senha igual ao login: só dá para conferir a ausência quando diferem
            if (!banco.getPassword().equals(banco.getUsername())) {
                assertThat(contexto.getStartupFailure()).rootCause().hasMessageNotContaining(banco.getPassword());
            }
        });
    }

    @Test
    @DisplayName("membro do coe_app, sem ser dono de nada: sobe")
    void membroDoCoeApp() throws SQLException {
        executarComoDono("CREATE ROLE login_app_ok LOGIN PASSWORD '" + SENHA + "' IN ROLE coe_app");

        contexto("login_app_ok", SENHA, true)
                .run(contexto -> assertThat(contexto).hasNotFailed());
    }

    @Test
    @DisplayName("fora do coe_app: não sobe")
    void foraDoCoeApp() throws SQLException {
        executarComoDono("CREATE ROLE login_sem_papel LOGIN PASSWORD '" + SENHA + "'");

        contexto("login_sem_papel", SENHA, true).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining("coe_app");
        });
    }

    @Test
    @DisplayName("membro do coe_app mas dono de uma tabela do schema: não sobe")
    void donoDeTabela() throws SQLException {
        executarComoDono(
                "CREATE ROLE login_dono_de_tabela LOGIN PASSWORD '" + SENHA + "' IN ROLE coe_app",
                "CREATE TABLE tabela_do_login_dono (id int)",
                "ALTER TABLE tabela_do_login_dono OWNER TO login_dono_de_tabela");

        contexto("login_dono_de_tabela", SENHA, true).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("dono")
                    .hasMessageContaining("tabela_do_login_dono");
        });
    }

    @Test
    @DisplayName("checagem desligada (local e test): sobe com qualquer login")
    void desligada() {
        contexto(banco.getUsername(), banco.getPassword(), false)
                .run(contexto -> assertThat(contexto).hasNotFailed());
    }

    private DataSource pool(String usuario, String senha) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(banco.getJdbcUrl());
        config.setUsername(usuario);
        config.setPassword(senha);
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }

    private void executarComoDono(String... comandos) throws SQLException {
        try (HikariDataSource dono = (HikariDataSource) pool(banco.getUsername(), banco.getPassword());
                Connection conexao = dono.getConnection();
                Statement statement = conexao.createStatement()) {
            for (String comando : comandos) {
                statement.execute(comando);
            }
        }
    }
}
