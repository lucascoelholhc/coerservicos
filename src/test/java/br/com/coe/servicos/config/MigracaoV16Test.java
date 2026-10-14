package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * V16 (CORE-07, dia 11): baldes do rate limit (Bucket4j com SELECT FOR UPDATE, chave sem dado pessoal
 * em claro) e a devolução da cota de envio quando a mensagem é descartada pela fila cheia
 * ({@code descartado_em}, que tira a linha das contagens de 60 s e 5 por hora).
 */
class MigracaoV16Test extends BancoIntegracaoTest {

    private static final String CHECK_VIOLATION = "23514";

    @Nested
    @DisplayName("limite_balde")
    class Baldes {

        @Test
        @DisplayName("a aplicação faz o ciclo do Bucket4j: insere, trava, grava o estado e apaga")
        void cicloDoBucket4j() {
            fixtures.agirComoAplicacao();

            assertThatCode(() -> {
                        jdbc.update(
                                "INSERT INTO limite_balde (chave, estado) VALUES (?, null) ON CONFLICT (chave) DO NOTHING",
                                "entrar:ip:203.0.113.7");
                        jdbc.queryForList(
                                "SELECT estado FROM limite_balde WHERE chave = ? FOR UPDATE", "entrar:ip:203.0.113.7");
                        jdbc.update(
                                "UPDATE limite_balde SET estado = ?, expira_em = ? WHERE chave = ?",
                                new byte[] {1, 2, 3},
                                System.currentTimeMillis() + 60_000,
                                "entrar:ip:203.0.113.7");
                        jdbc.update(
                                "DELETE FROM limite_balde WHERE chave IN (SELECT chave FROM limite_balde"
                                        + " WHERE expira_em < ? LIMIT 100 FOR UPDATE SKIP LOCKED)",
                                Long.MAX_VALUE);
                    })
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a aplicação não tem TRUNCATE na tabela dos baldes")
        void semTruncate() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE limite_balde"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        @Test
        @DisplayName("a aplicação não altera a estrutura da tabela dos baldes (não é dona)")
        void semDdl() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.execute("ALTER TABLE limite_balde ADD COLUMN extra int"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        @Test
        @DisplayName("privilégios do coe_app na tabela: exatamente SELECT, INSERT, UPDATE e DELETE")
        void privilegiosExatos() {
            assertThat(jdbc.queryForList(
                            "SELECT privilege_type FROM information_schema.role_table_grants"
                                    + " WHERE grantee = 'coe_app' AND table_name = 'limite_balde'"
                                    + " ORDER BY privilege_type",
                            String.class))
                    .containsExactly("DELETE", "INSERT", "SELECT", "UPDATE");
        }

        @Test
        @DisplayName("chave vazia é recusada")
        void chaveVazia() {
            PSQLException erro = erroDoBanco(() -> jdbc.update("INSERT INTO limite_balde (chave) VALUES ('')"));

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
        }

        @Test
        @DisplayName("chave acima de 200 caracteres é recusada")
        void chaveLonga() {
            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("INSERT INTO limite_balde (chave) VALUES (?)", "x".repeat(201)));

            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
        }

        @Test
        @DisplayName("índice parcial no vencimento (sem os nulos), para a limpeza dos baldes vencidos")
        void indiceDoVencimento() {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM pg_indexes WHERE tablename = 'limite_balde'"
                                    + " AND indexdef LIKE '%(expira_em) WHERE (expira_em IS NOT NULL)%'",
                            Integer.class))
                    .isOne();
        }

        @Test
        @DisplayName("fillfactor 70 e autovacuum frequente (todo UPDATE muda coluna indexada)")
        void armazenamento() {
            assertThat(jdbc.queryForObject(
                            "SELECT array_to_string(reloptions, ',') FROM pg_class"
                                    + " WHERE oid = 'public.limite_balde'::regclass",
                            String.class))
                    .contains("fillfactor=70", "autovacuum_vacuum_scale_factor=0.02");
        }

        @Test
        @DisplayName("tabela UNLOGGED: estado descartável, sem custo de WAL")
        void semWal() {
            assertThat(jdbc.queryForObject(
                            "SELECT relpersistence FROM pg_class WHERE oid = 'public.limite_balde'::regclass",
                            String.class))
                    .isEqualTo("u");
        }
    }

    @Nested
    @DisplayName("descarte devolve a cota")
    class Descarte {

        private UUID codigoSms() {
            return jdbc.queryForObject(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, criado_em, expira_em)"
                            + " VALUES (?, '47900000961', 'login', sha256('c'::bytea), now(),"
                            + " now() + interval '5 minutes') RETURNING id",
                    UUID.class,
                    fixtures.usuario());
        }

        private UUID token() {
            return jdbc.queryForObject(
                    "INSERT INTO token_verificacao (usuario_id, canal, destino, finalidade, token_hash, criado_em,"
                            + " expira_em) VALUES (?, 'email', 'v16@teste.coe.local', 'confirmar_email',"
                            + " sha256(gen_random_uuid()::text::bytea), now(), now() + interval '30 minutes')"
                            + " RETURNING id",
                    UUID.class,
                    fixtures.usuario());
        }

        @Test
        @DisplayName("a aplicação marca o descarte junto com a invalidação (código e link)")
        void aplicacaoMarcaODescarte() {
            UUID codigo = codigoSms();
            UUID link = token();
            fixtures.agirComoAplicacao();

            assertThatCode(() -> {
                        jdbc.update(
                                "UPDATE codigo_sms SET descartado_em = now(), invalidado_em = now() WHERE id = ?",
                                codigo);
                        jdbc.update(
                                "UPDATE token_verificacao SET descartado_em = now(), invalidado_em = now() WHERE id = ?",
                                link);
                    })
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("descarta código já substituído por um mais novo (mantém a invalidação de antes)")
        void descartaCodigoJaInvalidado() {
            UUID codigo = codigoSms();
            jdbc.update("UPDATE codigo_sms SET invalidado_em = now() WHERE id = ?", codigo);
            fixtures.agirComoAplicacao();

            assertThatCode(() -> jdbc.update(
                            "UPDATE codigo_sms SET descartado_em = now(), invalidado_em = coalesce(invalidado_em, now())"
                                    + " WHERE id = ? AND usado_em IS NULL",
                            codigo))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("relógio atrasado não impede o descarte (descartado_em antes de criado_em)")
        void relogioAtrasado() {
            UUID link = token();

            assertThatCode(() -> jdbc.update(
                            "UPDATE token_verificacao SET descartado_em = criado_em - interval '2 seconds',"
                                    + " invalidado_em = criado_em WHERE id = ?",
                            link))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("código descartado tem de estar invalidado")
        void codigoDescartadoSemInvalidar() {
            UUID codigo = codigoSms();

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE codigo_sms SET descartado_em = now() WHERE id = ?", codigo));

            assertMensagem(erro, CHECK_VIOLATION, "ck_codigo_sms_descarte");
        }

        @Test
        @DisplayName("link descartado tem de estar invalidado")
        void linkDescartadoSemInvalidar() {
            UUID link = token();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE token_verificacao SET descartado_em = now() WHERE id = ?", link));

            assertMensagem(erro, CHECK_VIOLATION, "ck_token_verificacao_descarte");
        }

        @Test
        @DisplayName("o descarte do código não muda depois de marcado")
        void descarteDoCodigoNaoMuda() {
            UUID codigo = codigoSms();
            jdbc.update("UPDATE codigo_sms SET descartado_em = now(), invalidado_em = now() WHERE id = ?", codigo);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE codigo_sms SET descartado_em = now() + interval '1 minute' WHERE id = ?", codigo));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "codigo_sms");
        }

        @Test
        @DisplayName("o descarte do link não muda depois de marcado")
        void descarteDoLinkNaoMuda() {
            UUID link = token();
            jdbc.update("UPDATE token_verificacao SET descartado_em = now(), invalidado_em = now() WHERE id = ?", link);

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE token_verificacao SET descartado_em = NULL WHERE id = ?", link));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "token_verificacao");
        }

        @Test
        @DisplayName("a aplicação só ganha UPDATE na coluna nova, não em outras")
        void aplicacaoSemUpdateGeral() {
            UUID codigo = codigoSms();
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE codigo_sms SET celular = '47900000962' WHERE id = ?", codigo));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }
}
