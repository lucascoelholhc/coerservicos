package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;

/**
 * DB-13: V12 da autenticação com JWT (PA03). Sessão do Spring sai, refresh token entra (só hash,
 * conteúdo imutável), MFA por SMS e teto de 30% na comissão.
 */
class MigracaoV12Test extends BancoIntegracaoTest {

    private UUID refreshToken(UUID usuario, UUID familia) {
        return jdbc.queryForObject("""
                INSERT INTO refresh_token
                    (usuario_id, familia_id, token_hash, aparelho, ip, criado_em, expira_em, sessao_iniciada_em)
                VALUES (?, ?, sha256(gen_random_uuid()::text::bytea), 'Chrome no Windows', '127.0.0.1',
                        now(), now() + interval '30 days', now())
                RETURNING id""", UUID.class, usuario, familia);
    }

    @Test
    @DisplayName("as tabelas de sessão do Spring saíram (o login é por JWT)")
    void semSpringSession() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'spring_session%'",
                        Integer.class))
                .isZero();
    }

    @Nested
    @DisplayName("refresh_token: só o hash, conteúdo imutável, revogação com motivo")
    class RefreshToken {

        @Test
        @DisplayName("grava um token válido")
        void gravaToken() {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> refreshToken(usuario, UUID.randomUUID())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o hash é único")
        void hashUnico() {
            UUID usuario = fixtures.usuario();
            UUID token = refreshToken(usuario, UUID.randomUUID());

            PSQLException erro = erroDoBanco(() -> jdbc.update("""
                    INSERT INTO refresh_token (usuario_id, familia_id, token_hash, criado_em, expira_em, sessao_iniciada_em)
                    SELECT usuario_id, gen_random_uuid(), token_hash, criado_em, expira_em, sessao_iniciada_em
                    FROM refresh_token WHERE id = ?""", token));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_refresh_token_hash");
        }

        @ParameterizedTest(name = "barra {0}")
        @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
                hash com tamanho errado       | ~'\\x01'::bytea~             | now() + interval '1 day' | ~'x'~                | ck_refresh_token_hash
                validade antes da criação     | sha256('a'::bytea)           | now() - interval '1 day' | ~'x'~                | ck_refresh_token_validade
                aparelho com mais de 120      | sha256('b'::bytea)           | now() + interval '1 day' | repeat('x', 121)     | ck_refresh_token_aparelho
                """)
        void barraInvalido(String caso, String hash, String expira, String aparelho, String constraint) {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO refresh_token (usuario_id, familia_id, token_hash, aparelho, criado_em, expira_em,"
                            + " sessao_iniciada_em) VALUES (?, ?, " + hash + ", " + aparelho + ", now(), " + expira
                            + ", now())",
                    usuario,
                    UUID.randomUUID()));

            assertConstraint(erro, CHECK_VIOLATION, constraint);
        }

        @Test
        @DisplayName("usuário inexistente é recusado (FK)")
        void usuarioInexistente() {
            PSQLException erro = erroDoBanco(() -> refreshToken(UUID.randomUUID(), UUID.randomUUID()));

            assertConstraint(erro, FOREIGN_KEY_VIOLATION, "fk_refresh_token_usuario");
        }

        @Test
        @DisplayName("revogado exige motivo")
        void revogadoExigeMotivo() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE refresh_token SET revogado_em = now() WHERE id = ?", token));

            assertConstraint(erro, CHECK_VIOLATION, "ck_refresh_token_revogacao");
        }

        @Test
        @DisplayName("motivo fora da lista é recusado")
        void motivoForaDaLista() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = 'cansei' WHERE id = ?", token));

            assertConstraint(erro, CHECK_VIOLATION, "ck_refresh_token_motivo");
        }

        @ParameterizedTest(name = "aceita revogar por {0}")
        @ValueSource(strings = {"logout", "sair_todos", "troca_senha", "reuso", "admin"})
        void aceitaMotivos(String motivo) {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());
            fixtures.agirComoAplicacao();

            assertThatCode(() -> jdbc.update(
                            "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = ? WHERE id = ?",
                            motivo,
                            token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a aplicação marca uso e sucessor")
        void marcaUsoESucessor() {
            UUID usuario = fixtures.usuario();
            UUID familia = UUID.randomUUID();
            UUID antigo = refreshToken(usuario, familia);
            UUID novo = refreshToken(usuario, familia);
            fixtures.agirComoAplicacao();

            assertThatCode(() -> jdbc.update(
                            "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?",
                            novo,
                            antigo))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("um token só tem um sucessor")
        void umSucessor() {
            UUID usuario = fixtures.usuario();
            UUID familia = UUID.randomUUID();
            UUID primeiro = refreshToken(usuario, familia);
            UUID segundo = refreshToken(usuario, familia);
            UUID sucessor = refreshToken(usuario, familia);
            jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?", sucessor, primeiro);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?", sucessor, segundo));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_refresh_token_sucessor");
        }

        @ParameterizedTest(name = "barra alterar {0}")
        @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
                token_hash  | sha256('outro'::bytea)
                familia_id  | gen_random_uuid()
                expira_em   | now() + interval '90 days'
                criado_em   | now() - interval '1 day'
                aparelho    | 'outro'
                ip          | '10.0.0.1'
                """)
        void conteudoImutavel(String coluna, String novoValor) {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());

            PSQLException erro = erroDoBanco(() ->
                    jdbc.update("UPDATE refresh_token SET " + coluna + " = " + novoValor + " WHERE id = ?", token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
        }

        @Test
        @DisplayName("o dono do token não muda")
        void donoNaoMuda() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());
            UUID outro = fixtures.usuario();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE refresh_token SET usuario_id = ? WHERE id = ?", outro, token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
        }

        @Test
        @DisplayName("revogação não volta atrás")
        void naoDesfazRevogacao() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());
            jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), revogado_em = now(), motivo_revogacao = 'logout'"
                            + " WHERE id = ?",
                    token);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE refresh_token SET revogado_em = NULL, motivo_revogacao = NULL WHERE id = ?", token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
        }

        @Test
        @DisplayName("uso não volta atrás")
        void naoDesfazUso() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());
            jdbc.update("UPDATE refresh_token SET usado_em = now() WHERE id = ?", token);

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE refresh_token SET usado_em = NULL WHERE id = ?", token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
        }

        @Test
        @DisplayName("criado_em vem da aplicação (Clock), sem default do banco")
        void criadoEmObrigatorio() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO refresh_token (usuario_id, familia_id, token_hash, expira_em)"
                            + " VALUES (?, gen_random_uuid(), sha256('c'::bytea), now() + interval '1 day')",
                    usuario));

            assertThat(erro.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
            assertThat(erro.getServerErrorMessage().getColumn()).isEqualTo("criado_em");
        }

        @ParameterizedTest(name = "barra {0}")
        @CsvSource(delimiter = '|', textBlock = """
                uso antes da criação        | usado_em = criado_em - interval '1 second'                                 | ck_refresh_token_uso
                revogação antes da criação  | revogado_em = criado_em - interval '1 second', motivo_revogacao = 'logout' | ck_refresh_token_revogacao_data
                sucessor sem uso            | substituido_por = id                                                       | ck_refresh_token_sucessor
                sucessor sendo ele mesmo    | usado_em = criado_em, substituido_por = id                                 | ck_refresh_token_sucessor
                """)
        void barraDatasESucessor(String caso, String alteracao, String constraint) {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE refresh_token SET " + alteracao + " WHERE id = ?", token));

            assertConstraint(erro, CHECK_VIOLATION, constraint);
        }

        @Test
        @DisplayName("o sucessor é da mesma família e do mesmo usuário")
        void sucessorDaMesmaFamilia() {
            UUID usuario = fixtures.usuario();
            UUID antigo = refreshToken(usuario, UUID.randomUUID());
            UUID deOutraFamilia = refreshToken(usuario, UUID.randomUUID());

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?",
                    deOutraFamilia,
                    antigo));

            assertConstraint(erro, FOREIGN_KEY_VIOLATION, "fk_refresh_token_sucessor");
        }

        @Test
        @DisplayName("o sucessor não muda depois de definido")
        void sucessorNaoMuda() {
            UUID usuario = fixtures.usuario();
            UUID familia = UUID.randomUUID();
            UUID antigo = refreshToken(usuario, familia);
            UUID sucessor = refreshToken(usuario, familia);
            UUID outro = refreshToken(usuario, familia);
            jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?", sucessor, antigo);

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE refresh_token SET substituido_por = ? WHERE id = ?", outro, antigo));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
        }

        @Test
        @DisplayName("a aplicação revoga em massa por família e por usuário")
        void revogacaoEmMassa() {
            UUID usuario = fixtures.usuario();
            UUID familia = UUID.randomUUID();
            refreshToken(usuario, familia);
            refreshToken(usuario, familia);
            refreshToken(usuario, UUID.randomUUID());
            fixtures.agirComoAplicacao();

            int daFamilia = jdbc.update(
                    "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = 'reuso'"
                            + " WHERE familia_id = ? AND revogado_em IS NULL",
                    familia);
            int doUsuario = jdbc.update(
                    "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = 'sair_todos'"
                            + " WHERE usuario_id = ? AND revogado_em IS NULL",
                    usuario);

            assertThat(daFamilia).isEqualTo(2);
            assertThat(doUsuario).isEqualTo(1);
        }

        @Test
        @DisplayName("a aplicação não esvazia a tabela (TRUNCATE)")
        void aplicacaoNaoTrunca() {
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE refresh_token"));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        @Test
        @DisplayName("o dono do schema apaga a cadeia inteira numa instrução (expurgo futuro)")
        void donoApagaCadeia() {
            UUID usuario = fixtures.usuario();
            UUID familia = UUID.randomUUID();
            UUID antigo = refreshToken(usuario, familia);
            UUID sucessor = refreshToken(usuario, familia);
            jdbc.update(
                    "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?", sucessor, antigo);

            assertThat(jdbc.update("DELETE FROM refresh_token WHERE familia_id = ?", familia))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("índices para renovar (hash), revogar a família e sair de todos")
        void indices() {
            assertThat(jdbc.queryForList(
                            "SELECT indexname FROM pg_indexes WHERE tablename = 'refresh_token'", String.class))
                    .contains("uq_refresh_token_hash", "ix_refresh_token_usuario", "ix_refresh_token_familia");
        }

        @Test
        @DisplayName("a aplicação não apaga refresh token (expurgo é job do dono do schema)")
        void aplicacaoNaoApaga() {
            UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID());
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM refresh_token WHERE id = ?", token));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    @DisplayName("a aplicação liga e desliga o MFA do usuário")
    void aplicacaoLigaMfa() {
        UUID usuario = fixtures.usuario();
        fixtures.agirComoAplicacao();

        assertThat(jdbc.update("UPDATE usuario SET mfa_sms_ativo = true WHERE id = ?", usuario))
                .isOne();
    }

    @Test
    @DisplayName("usuario.mfa_sms_ativo nasce falso")
    void mfaNasceDesligado() {
        UUID usuario = fixtures.usuario();

        assertThat(jdbc.queryForObject("SELECT mfa_sms_ativo FROM usuario WHERE id = ?", Boolean.class, usuario))
                .isFalse();
    }

    @Nested
    @DisplayName("codigo_sms: finalidade mfa")
    class CodigoSms {

        private static final String INSERIR = """
                INSERT INTO codigo_sms (celular, finalidade, codigo_hash, expira_em)
                VALUES ('47900000301', ?, 'hash', now() + interval '5 minutes')""";

        @ParameterizedTest(name = "aceita {0}")
        @ValueSource(strings = {"login", "verificar_celular", "trocar_celular", "mfa"})
        void aceitaFinalidades(String finalidade) {
            assertThatCode(() -> jdbc.update(INSERIR, finalidade)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("finalidade fora da lista é recusada")
        void barraOutraFinalidade() {
            PSQLException erro = erroDoBanco(() -> jdbc.update(INSERIR, "qualquer"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_finalidade");
        }
    }

    @Nested
    @DisplayName("comissão com teto de 30%")
    class TetoDaComissao {

        private static final String INSERIR = """
                INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde)
                VALUES ('COMISSAO', ?, 'decimal', 'teste', now() + interval '1 day')""";

        @ParameterizedTest(name = "aceita {0}")
        @ValueSource(strings = {"0.30", "0.3", "0.10", "0"})
        void aceitaAteOTeto(String valor) {
            assertThatCode(() -> jdbc.update(INSERIR, valor)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "barra {0}")
        @ValueSource(strings = {"0.31", "0.300001", "0.99"})
        void barraAcimaDoTeto(String valor) {
            PSQLException erro = erroDoBanco(() -> jdbc.update(INSERIR, valor));

            assertConstraint(erro, CHECK_VIOLATION, "ck_configuracao_faixas");
        }
    }
}
