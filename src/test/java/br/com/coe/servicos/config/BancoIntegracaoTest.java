package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.TestcontainersConfiguration;

/**
 * Base dos testes que exercitam o schema num Postgres real (Testcontainers).
 * Todos compartilham o mesmo contexto Spring e o mesmo container; cada teste roda
 * numa transação desfeita no fim, salvo indicação.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
abstract class BancoIntegracaoTest {

    protected static final String UNIQUE_VIOLATION = "23505";
    protected static final String CHECK_VIOLATION = "23514";
    protected static final String NOT_NULL_VIOLATION = "23502";
    protected static final String FOREIGN_KEY_VIOLATION = "23503";
    protected static final String INSUFFICIENT_PRIVILEGE = "42501";
    protected static final LocalDate DIA = LocalDate.now().plusDays(7);

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected Flyway flyway;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected FixturesBanco fixtures;

    @BeforeEach
    void prepararFixtures() {
        fixtures = new FixturesBanco(jdbc);
    }

    /**
     * Executa um comando que o banco deve recusar e devolve o erro do Postgres.
     * Deve ser o último comando do teste: depois do erro, a transação do teste fica abortada
     * (SQLSTATE 25P02) e qualquer outra consulta falha. Prepare os dados antes, fora da lambda.
     */
    protected static PSQLException erroDoBanco(ThrowingCallable comando) {
        Throwable erro = catchThrowable(comando);
        assertThat(erro).as("o banco deveria ter recusado o comando").isNotNull();
        Throwable causa = NestedExceptionUtils.getMostSpecificCause(erro);
        assertThat(causa).as(erro.getMessage()).isInstanceOf(PSQLException.class);
        return (PSQLException) causa;
    }

    protected static void assertConstraint(PSQLException erro, String sqlState, String constraint) {
        assertThat(erro.getSQLState()).as(erro.getMessage()).isEqualTo(sqlState);
        assertThat(erro.getServerErrorMessage()).as(erro.getMessage()).isNotNull();
        assertThat(erro.getServerErrorMessage().getConstraint()).as(erro.getMessage()).isEqualTo(constraint);
    }

    protected static void assertMensagem(PSQLException erro, String sqlState, String trecho) {
        assertThat(erro.getSQLState()).as(erro.getMessage()).isEqualTo(sqlState);
        assertThat(erro.getServerErrorMessage()).as(erro.getMessage()).isNotNull();
        assertThat(erro.getServerErrorMessage().getMessage()).as(erro.getMessage()).contains(trecho);
    }

    protected static BigDecimal valor(String valor) {
        return new BigDecimal(valor);
    }
}
