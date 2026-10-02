package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.TestcontainersConfiguration;

/**
 * O schema sobe do zero pelo Flyway e as regras críticas são garantidas pelo próprio banco,
 * mesmo que a aplicação erre. Cada teste roda numa transação desfeita no fim, salvo indicação.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MigracoesFlywayTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final LocalDate DIA = LocalDate.now().plusDays(7);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    PlatformTransactionManager transactionManager;

    FixturesBanco fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new FixturesBanco(jdbc);
    }

    @Test
    @DisplayName("banco vazio sobe pelo menos até a V10, sem pendências nem falhas")
    void bancoVazioSobeAteV10() {
        MigrationInfoService info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.all()).noneMatch(migracao -> migracao.getState().isFailed());
        assertThat(info.current().getVersion()).isGreaterThanOrEqualTo(MigrationVersion.fromVersion("10"));
    }

    @Test
    @DisplayName("perfil de teste não aplica os dados do perfil local (repeatable de db/local)")
    void naoAplicaDadosDoPerfilLocal() {
        assertThat(flyway.info().applied())
                .noneMatch(migracao -> migracao.getVersion() == null);
    }

    @Nested
    @DisplayName("agenda do profissional (uq_diaria_agenda_profissional)")
    class AgendaProfissional {

        @Test
        @DisplayName("barra duas diárias ativas do mesmo profissional no mesmo dia")
        void barraDuasDiariasAtivasNoMesmoDia() {
            UUID profissional = fixtures.profissional();
            UUID clienteA = fixtures.usuario();
            UUID clienteB = fixtures.usuario();
            fixtures.diaria(fixtures.contrato(clienteA, profissional), clienteA, profissional, DIA);
            UUID contratoB = fixtures.contrato(clienteB, profissional);

            PSQLException erro = erroDoBanco(() -> fixtures.diaria(contratoB, clienteB, profissional, DIA));

            assertThat(erro.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
            assertThat(erro.getServerErrorMessage().getConstraint()).isEqualTo("uq_diaria_agenda_profissional");
        }

        @Test
        @DisplayName("libera o dia quando a diária anterior foi cancelada")
        void liberaODiaQuandoADiariaAnteriorFoiCancelada() {
            UUID profissional = fixtures.profissional();
            UUID clienteA = fixtures.usuario();
            UUID clienteB = fixtures.usuario();
            UUID primeira = fixtures.diaria(fixtures.contrato(clienteA, profissional), clienteA, profissional, DIA);
            fixtures.cancelarDiaria(primeira);
            UUID contratoB = fixtures.contrato(clienteB, profissional);

            assertThatCode(() -> fixtures.diaria(contratoB, clienteB, profissional, DIA))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("total do contrato (ck_contrato_total)")
    class TotalContrato {

        @Test
        @DisplayName("barra total sem a comissão quando o cliente paga a taxa")
        void barraTotalSemAComissaoQuandoOClientePagaATaxa() {
            UUID cliente = fixtures.usuario();
            UUID profissional = fixtures.profissional();

            PSQLException erro = erroDoBanco(() -> fixtures.contrato(cliente, profissional,
                    FixturesBanco.TAXA_CLIENTE, valor("280.00"), valor("28.00"), valor("280.00")));

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getConstraint()).isEqualTo("ck_contrato_total");
        }

        @Test
        @DisplayName("aceita total com a comissão quando o cliente paga a taxa")
        void aceitaTotalComAComissaoQuandoOClientePagaATaxa() {
            UUID cliente = fixtures.usuario();
            UUID profissional = fixtures.profissional();

            assertThatCode(() -> fixtures.contrato(cliente, profissional,
                    FixturesBanco.TAXA_CLIENTE, valor("280.00"), valor("28.00"), valor("308.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aceita total igual aos serviços quando o profissional paga a taxa")
        void aceitaTotalIgualAosServicosQuandoOProfissionalPagaATaxa() {
            UUID cliente = fixtures.usuario();
            UUID profissional = fixtures.profissional();

            assertThatCode(() -> fixtures.contrato(cliente, profissional,
                    FixturesBanco.TAXA_PROFISSIONAL, valor("280.00"), valor("28.00"), valor("280.00")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("partidas dobradas do ledger (trg_transacao_partidas)")
    class PartidasDobradas {

        @Test
        @DisplayName("barra transação com débitos diferentes dos créditos")
        void barraTransacaoComDebitosDiferentesDosCreditos() {
            UUID transacao = fixtures.transacaoFinanceira();
            fixtures.lancamento(transacao, "gateway", "D", "308.00");
            fixtures.lancamento(transacao, "custodia", "C", "300.00");

            PSQLException erro = erroDoBanco(fixtures::conferirPartidasAgora);

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("desbalanceada");
        }

        @Test
        @DisplayName("aceita transação balanceada em várias partidas")
        void aceitaTransacaoBalanceadaEmVariasPartidas() {
            UUID transacao = fixtures.transacaoFinanceira();
            fixtures.lancamento(transacao, "gateway", "D", "308.00");
            fixtures.lancamento(transacao, "custodia", "C", "280.00");
            fixtures.lancamento(transacao, "receita_coe", "C", "28.00");

            assertThatCode(fixtures::conferirPartidasAgora).doesNotThrowAnyException();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("barra o COMMIT de transação desbalanceada (checagem adiada de verdade)")
        void barraCommitDeTransacaoDesbalanceada() {
            TransactionTemplate transacaoReal = new TransactionTemplate(transactionManager);

            PSQLException erro = erroDoBanco(() -> transacaoReal.executeWithoutResult(status -> {
                UUID transacao = fixtures.transacaoFinanceira();
                fixtures.lancamento(transacao, "gateway", "D", "308.00");
                fixtures.lancamento(transacao, "custodia", "C", "300.00");
            }));

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("desbalanceada");
        }

        @Test
        @DisplayName("barra transação financeira sem nenhum lançamento")
        void barraTransacaoSemLancamentos() {
            fixtures.transacaoFinanceira();

            PSQLException erro = erroDoBanco(fixtures::conferirPartidasAgora);

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("sem lançamentos");
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("barra lançamento anexado a uma transação financeira já fechada")
        void barraLancamentoEmTransacaoJaFechada() {
            TransactionTemplate transacaoReal = new TransactionTemplate(transactionManager);
            UUID fechada = transacaoReal.execute(status -> {
                UUID transacao = fixtures.transacaoFinanceira();
                fixtures.lancamento(transacao, "gateway", "D", "10.00");
                fixtures.lancamento(transacao, "custodia", "C", "10.00");
                return transacao;
            });

            PSQLException erro = erroDoBanco(() -> transacaoReal.executeWithoutResult(status -> {
                fixtures.lancamento(fechada, "custodia", "D", "10.00");
                fixtures.lancamento(fechada, "receita_coe", "C", "10.00");
            }));

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("já fechada");
        }

        @Test
        @DisplayName("barra duas transações com a mesma chave de idempotência")
        void barraChaveDeIdempotenciaRepetida() {
            fixtures.transacaoFinanceira("liberacao:diaria-1");

            PSQLException erro = erroDoBanco(() -> fixtures.transacaoFinanceira("liberacao:diaria-1"));

            assertThat(erro.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
            assertThat(erro.getServerErrorMessage().getConstraint()).isEqualTo("uq_transacao_idempotencia");
        }
    }

    @Nested
    @DisplayName("ledger imutável (fn_somente_insercao)")
    class LedgerImutavel {

        @Test
        @DisplayName("barra UPDATE em lançamento")
        void barraUpdateEmLancamento() {
            long lancamento = lancamentoExistente();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE lancamento SET valor = 1.00 WHERE id = ?", lancamento));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("lancamento aceita apenas INSERT");
        }

        @Test
        @DisplayName("barra DELETE em lançamento")
        void barraDeleteEmLancamento() {
            long lancamento = lancamentoExistente();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("DELETE FROM lancamento WHERE id = ?", lancamento));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("lancamento aceita apenas INSERT");
        }

        private long lancamentoExistente() {
            UUID transacao = fixtures.transacaoFinanceira();
            long debito = fixtures.lancamento(transacao, "gateway", "D", "308.00");
            fixtures.lancamento(transacao, "custodia", "C", "308.00");
            return debito;
        }
    }

    @Nested
    @DisplayName("tabelas só de inserção não aceitam TRUNCATE")
    class SemTruncate {

        @Test
        @DisplayName("barra TRUNCATE direto em lançamento")
        void barraTruncateEmLancamento() {
            PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE lancamento CASCADE"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("aceita apenas INSERT");
        }

        @Test
        @DisplayName("barra TRUNCATE em cascata a partir de usuario (alcança ledger e auditoria)")
        void barraTruncateEmCascata() {
            PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE usuario CASCADE"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("aceita apenas INSERT");
        }
    }

    @Nested
    @DisplayName("papel da aplicação (coe_app) não é dono do schema")
    class PapelDaAplicacao {

        @Test
        @DisplayName("registra uma transação financeira balanceada")
        void registraTransacaoBalanceada() {
            fixtures.agirComoAplicacao();
            UUID transacao = fixtures.transacaoFinanceira();
            fixtures.lancamento(transacao, "gateway", "D", "308.00");
            fixtures.lancamento(transacao, "custodia", "C", "308.00");

            assertThatCode(fixtures::conferirPartidasAgora).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("não tem permissão de UPDATE em lançamento")
        void naoTemPermissaoDeUpdateEmLancamento() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("UPDATE lancamento SET valor = 1.00"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("permission denied");
        }

        @Test
        @DisplayName("não consegue desligar os triggers do ledger")
        void naoConsegueDesligarTriggers() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.execute("ALTER TABLE lancamento DISABLE TRIGGER ALL"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("must be owner");
        }

        @Test
        @DisplayName("lê o saldo pela view com os próprios privilégios (security_invoker)")
        void viewDeSaldoUsaPrivilegiosDeQuemConsulta() {
            String opcoes = jdbc.queryForObject(
                    "SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = 'saldo_conta'",
                    String.class);

            assertThat(opcoes).contains("security_invoker=true");
        }
    }

    @Nested
    @DisplayName("webhook idempotente (uq_evento_gateway)")
    class WebhookIdempotente {

        @Test
        @DisplayName("barra o mesmo evento do mesmo gateway duas vezes")
        void barraOMesmoEventoDoMesmoGatewayDuasVezes() {
            fixtures.eventoGateway("falso", "evt_123");

            PSQLException erro = erroDoBanco(() -> fixtures.eventoGateway("falso", "evt_123"));

            assertThat(erro.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
            assertThat(erro.getServerErrorMessage().getConstraint()).isEqualTo("uq_evento_gateway");
        }

        @Test
        @DisplayName("aceita o mesmo id de evento vindo de outro gateway")
        void aceitaOMesmoIdDeEventoVindoDeOutroGateway() {
            fixtures.eventoGateway("falso", "evt_123");

            assertThatCode(() -> fixtures.eventoGateway("outro", "evt_123")).doesNotThrowAnyException();
        }
    }

    private static PSQLException erroDoBanco(ThrowingCallable comando) {
        Throwable erro = catchThrowable(comando);
        assertThat(erro).as("o banco deveria ter recusado o comando").isNotNull();
        Throwable causa = NestedExceptionUtils.getMostSpecificCause(erro);
        assertThat(causa).isInstanceOf(PSQLException.class);
        return (PSQLException) causa;
    }

    private static BigDecimal valor(String valor) {
        return new BigDecimal(valor);
    }
}
