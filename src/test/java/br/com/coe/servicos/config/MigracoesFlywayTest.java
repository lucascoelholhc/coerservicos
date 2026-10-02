package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O schema sobe do zero pelo Flyway e as regras críticas de V1–V10 (e as que valem para o schema
 * inteiro) são garantidas pelo próprio banco, mesmo que a aplicação erre. As regras da V11 ficam
 * em {@link MigracaoV11Test}.
 */
class MigracoesFlywayTest extends BancoIntegracaoTest {

    @Test
    @DisplayName("banco vazio sobe até a última migração (pelo menos V11), sem pendências nem falhas")
    void bancoVazioSobeSemPendencias() {
        MigrationInfoService info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.all()).noneMatch(migracao -> migracao.getState().isFailed());
        assertThat(info.current().getVersion()).isGreaterThanOrEqualTo(MigrationVersion.fromVersion("11"));
    }

    @Test
    @DisplayName("perfil de teste não aplica os dados do perfil local (repeatable de db/local)")
    void naoAplicaDadosDoPerfilLocal() {
        assertThat(flyway.info().applied()).noneMatch(migracao -> migracao.getVersion() == null);
    }

    @Test
    @DisplayName("toda FK tem um índice que começa pela coluna dela")
    void todaFkTemIndice() {
        List<String> semIndice = jdbc.queryForList("""
                SELECT c.conrelid::regclass || '.' || c.conname
                FROM pg_constraint c
                WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace
                  AND NOT EXISTS (
                    SELECT 1 FROM pg_index i
                    WHERE i.indrelid = c.conrelid AND i.indpred IS NULL
                      AND (string_to_array(i.indkey::text, ' ')::int2[])[1] = c.conkey[1])
                ORDER BY 1""", String.class);

        assertThat(semIndice).isEmpty();
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

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_diaria_agenda_profissional");
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

            PSQLException erro = erroDoBanco(() -> fixtures.contrato(
                    cliente,
                    profissional,
                    FixturesBanco.TAXA_CLIENTE,
                    valor("280.00"),
                    valor("28.00"),
                    valor("280.00")));

            assertConstraint(erro, CHECK_VIOLATION, "ck_contrato_total");
        }

        @Test
        @DisplayName("aceita total com a comissão quando o cliente paga a taxa")
        void aceitaTotalComAComissaoQuandoOClientePagaATaxa() {
            UUID cliente = fixtures.usuario();
            UUID profissional = fixtures.profissional();

            assertThatCode(() -> fixtures.contrato(
                            cliente,
                            profissional,
                            FixturesBanco.TAXA_CLIENTE,
                            valor("280.00"),
                            valor("28.00"),
                            valor("308.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aceita total igual aos serviços quando o profissional paga a taxa")
        void aceitaTotalIgualAosServicosQuandoOProfissionalPagaATaxa() {
            UUID cliente = fixtures.usuario();
            UUID profissional = fixtures.profissional();

            assertThatCode(() -> fixtures.contrato(
                            cliente,
                            profissional,
                            FixturesBanco.TAXA_PROFISSIONAL,
                            valor("280.00"),
                            valor("28.00"),
                            valor("280.00")))
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

            assertMensagem(erro, CHECK_VIOLATION, "desbalanceada");
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

        /** Sem rollback do teste: o COMMIT falha, então nada fica gravado. */
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

            assertMensagem(erro, CHECK_VIOLATION, "desbalanceada");
        }

        @Test
        @DisplayName("barra transação financeira sem nenhum lançamento")
        void barraTransacaoSemLancamentos() {
            fixtures.transacaoFinanceira();

            PSQLException erro = erroDoBanco(fixtures::conferirPartidasAgora);

            assertMensagem(erro, CHECK_VIOLATION, "sem lançamentos");
        }

        /**
         * Sem rollback do teste: a primeira transação financeira (R$ 10, balanceada) fica gravada no
         * container compartilhado, porque o ledger é imutável e não dá para limpar. Testes que somam
         * o ledger devem filtrar pelos ids que eles mesmos criaram.
         */
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

            assertMensagem(erro, CHECK_VIOLATION, "já fechada");
        }

        @Test
        @DisplayName("barra duas transações com a mesma chave de idempotência")
        void barraChaveDeIdempotenciaRepetida() {
            fixtures.transacaoFinanceira("liberacao:diaria-1");

            PSQLException erro = erroDoBanco(() -> fixtures.transacaoFinanceira("liberacao:diaria-1"));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_transacao_idempotencia");
        }
    }

    @Nested
    @DisplayName("ledger imutável (fn_somente_insercao)")
    class LedgerImutavel {

        @Test
        @DisplayName("barra UPDATE em lançamento")
        void barraUpdateEmLancamento() {
            long lancamento = lancamentoExistente();

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE lancamento SET valor = 1.00 WHERE id = ?", lancamento));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "lancamento aceita apenas INSERT");
        }

        @Test
        @DisplayName("barra DELETE em lançamento")
        void barraDeleteEmLancamento() {
            long lancamento = lancamentoExistente();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM lancamento WHERE id = ?", lancamento));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "lancamento aceita apenas INSERT");
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

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "aceita apenas INSERT");
        }

        @Test
        @DisplayName("barra TRUNCATE em cascata a partir de usuario (alcança ledger e auditoria)")
        void barraTruncateEmCascata() {
            PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE usuario CASCADE"));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "aceita apenas INSERT");
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

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "permission denied for table lancamento");
        }

        @Test
        @DisplayName("não tem permissão de DELETE em usuario (exclusão é por anonimização)")
        void naoTemPermissaoDeDeleteEmUsuario() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM usuario"));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "permission denied for table usuario");
        }

        @Test
        @DisplayName("não tem permissão de DELETE nos webhooks recebidos")
        void naoTemPermissaoDeDeleteEmEventoGateway() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM evento_gateway"));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "permission denied for table evento_gateway");
        }

        @Test
        @DisplayName("não consegue desligar os triggers do ledger")
        void naoConsegueDesligarTriggers() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.execute("ALTER TABLE lancamento DISABLE TRIGGER ALL"));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "must be owner");
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

    @Test
    @DisplayName("webhook: aceita o mesmo id de evento vindo de outro gateway")
    void aceitaOMesmoIdDeEventoVindoDeOutroGateway() {
        fixtures.eventoGateway("falso", "evt_123");

        assertThatCode(() -> fixtures.eventoGateway("outro", "evt_123")).doesNotThrowAnyException();
    }
}
