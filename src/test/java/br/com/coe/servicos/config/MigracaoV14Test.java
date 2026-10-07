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
 * DB-16: V14 (CORE-04/05 e RN61). Código SMS com HMAC, desafio do MFA e dono; tokens por link e
 * comprovantes de posse numa tabela só (token_verificacao); token_senha sai; credenciais da conta
 * aceitam ficar sem celular ou sem e-mail (RN61), nunca sem os dois.
 */
class MigracaoV14Test extends BancoIntegracaoTest {

    private static final String HMAC = "sha256('codigo'::bytea)";

    private UUID codigoSms(UUID usuario, String finalidade) {
        return jdbc.queryForObject(
                "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, criado_em, expira_em)"
                        + " VALUES (?, '47900000401', ?, " + HMAC
                        + ", now(), now() + interval '5 minutes') RETURNING id",
                UUID.class,
                usuario,
                finalidade);
    }

    private UUID token(UUID usuario, String canal, String destino, String finalidade) {
        return jdbc.queryForObject(
                "INSERT INTO token_verificacao (usuario_id, canal, destino, finalidade, token_hash, criado_em, expira_em)"
                        + " VALUES (?, ?, ?, ?, sha256(gen_random_uuid()::text::bytea), now(), now() + interval '30 minutes')"
                        + " RETURNING id",
                UUID.class,
                usuario,
                canal,
                destino,
                finalidade);
    }

    @Test
    @DisplayName("token_senha saiu (os links ficam na token_verificacao)")
    void semTokenSenha() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'token_senha'",
                        Integer.class))
                .isZero();
    }

    @Nested
    @DisplayName("usuario: RN61 e e-mail confirmado")
    class Usuario {

        @Test
        @DisplayName("conta sem celular mas com e-mail é aceita (perdeu o celular para o dono, RN61)")
        void semCelular() {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> jdbc.update(
                            "UPDATE usuario SET celular = NULL, celular_verificado_em = NULL WHERE id = ?", usuario))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("conta sem e-mail mas com celular é aceita")
        void semEmail() {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> jdbc.update("UPDATE usuario SET email = NULL WHERE id = ?", usuario))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("conta sem celular e sem e-mail é recusada")
        void semNenhumContato() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE usuario SET celular = NULL, celular_verificado_em = NULL, email = NULL WHERE id = ?",
                    usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_usuario_credenciais");
        }

        @Test
        @DisplayName("conta sem senha é recusada (fora de excluido)")
        void semSenha() {
            UUID usuario = fixtures.usuario();

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE usuario SET senha_hash = NULL WHERE id = ?", usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_usuario_credenciais");
        }

        @ParameterizedTest(name = "barra {0} confirmado sem o dado")
        @CsvSource(delimiter = '|', textBlock = """
                celular | celular_verificado_em = now(), celular = NULL | ck_usuario_celular_verificado
                e-mail  | email_verificado_em = now(), email = NULL     | ck_usuario_email_verificado
                """)
        void confirmadoSemODado(String caso, String alteracao, String constraint) {
            UUID usuario = fixtures.usuario();

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE usuario SET " + alteracao + " WHERE id = ?", usuario));

            assertConstraint(erro, CHECK_VIOLATION, constraint);
        }

        @Test
        @DisplayName("email_verificado_em existe e nasce nulo")
        void emailNasceNaoConfirmado() {
            UUID usuario = fixtures.usuario();

            assertThat(jdbc.queryForObject(
                            "SELECT email_verificado_em IS NULL FROM usuario WHERE id = ?", Boolean.class, usuario))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("codigo_sms: HMAC, dono, desafio do MFA, imutável")
    class CodigoSms {

        @Test
        @DisplayName("grava um código de login com dono")
        void gravaCodigo() {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> codigoSms(usuario, "login")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "aceita a finalidade {0}")
        @ValueSource(
                strings = {
                    "login",
                    "verificar_celular",
                    "trocar_celular",
                    "configurar_mfa",
                    "comprovar_posse",
                    "recuperar_senha"
                })
        void finalidades(String finalidade) {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> codigoSms(usuario, finalidade)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("finalidade fora da lista é recusada")
        void finalidadeInvalida() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> codigoSms(usuario, "outra"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_finalidade");
        }

        @Test
        @DisplayName("sem dono só para provar posse (no cadastro ainda não há conta)")
        void semDono() {
            assertThatCode(() -> codigoSms(null, "comprovar_posse")).doesNotThrowAnyException();

            PSQLException erro = erroDoBanco(() -> codigoSms(null, "login"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_dono");
        }

        @Test
        @DisplayName("o HMAC tem 32 bytes; o código em claro não cabe")
        void hmacDe32Bytes() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, criado_em, expira_em)"
                            + " VALUES (?, '47900000402', 'login', convert_to('123456', 'UTF8'), now(),"
                            + " now() + interval '5 minutes')",
                    usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_hmac");
        }

        @Test
        @DisplayName("criado_em vem do Clock da aplicação, sem default")
        void criadoEmSemDefault() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, expira_em)"
                            + " VALUES (?, '47900000403', 'login', " + HMAC + ", now() + interval '5 minutes')",
                    usuario));

            assertThat(erro.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
            assertThat(erro.getServerErrorMessage().getColumn()).isEqualTo("criado_em");
        }

        @Test
        @DisplayName("desafio do MFA é único")
        void desafioUnico() {
            UUID usuario = fixtures.usuario();
            jdbc.update(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, desafio_hash, criado_em, expira_em)"
                            + " VALUES (?, '47900000409', 'mfa', " + HMAC + ", sha256('desafio'::bytea), now(),"
                            + " now() + interval '5 minutes')",
                    usuario);

            PSQLException repetido = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, desafio_hash, criado_em, expira_em)"
                            + " VALUES (?, '47900000404', 'mfa', " + HMAC + ", sha256('desafio'::bytea), now(),"
                            + " now() + interval '5 minutes')",
                    usuario));

            assertConstraint(repetido, UNIQUE_VIOLATION, "uq_codigo_sms_desafio");
        }

        @Test
        @DisplayName("desafio fora da finalidade mfa é recusado")
        void desafioForaDoMfa() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO codigo_sms (usuario_id, celular, finalidade, codigo_hmac, desafio_hash, criado_em, expira_em)"
                            + " VALUES (?, '47900000405', 'login', " + HMAC + ", sha256('d2'::bytea), now(),"
                            + " now() + interval '5 minutes')",
                    usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_desafio");
        }

        @Test
        @DisplayName("a aplicação conta tentativas e marca o uso")
        void tentativasEUso() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            fixtures.agirComoAplicacao();

            assertThatCode(() -> {
                        jdbc.update("UPDATE codigo_sms SET tentativas = tentativas + 1 WHERE id = ?", codigo);
                        jdbc.update("UPDATE codigo_sms SET usado_em = now() WHERE id = ?", codigo);
                    })
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "barra alterar {0}")
        @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
                codigo_hmac  | sha256('outro'::bytea)
                celular      | '47900000499'
                finalidade   | 'mfa'
                expira_em    | now() + interval '1 hour'
                usuario_id   | NULL
                tentativas   | 0
                """)
        void conteudoImutavel(String coluna, String valor) {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            jdbc.update("UPDATE codigo_sms SET tentativas = 2 WHERE id = ?", codigo);

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE codigo_sms SET " + coluna + " = " + valor + " WHERE id = ?", codigo));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só tentativas (subindo) e uso");
        }

        @Test
        @DisplayName("o uso não volta atrás")
        void usoNaoVoltaAtras() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            jdbc.update("UPDATE codigo_sms SET usado_em = now() WHERE id = ?", codigo);

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE codigo_sms SET usado_em = NULL WHERE id = ?", codigo));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só tentativas (subindo) e uso");
        }

        @Test
        @DisplayName("a aplicação não apaga código (expurgo é job do dono do schema)")
        void aplicacaoNaoApaga() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM codigo_sms WHERE id = ?", codigo));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        @Test
        @DisplayName("índice completo (não parcial) começando pelo dono, para a FK")
        void indiceDoDono() {
            assertThat(jdbc.queryForObject("""
                            SELECT count(*) FROM pg_index i
                             WHERE i.indrelid = 'public.codigo_sms'::regclass AND i.indpred IS NULL
                               AND i.indkey[0] = (SELECT attnum FROM pg_attribute
                                                   WHERE attrelid = i.indrelid AND attname = 'usuario_id')""", Integer.class)).isPositive();
        }
    }

    @Nested
    @DisplayName("token_verificacao: links de e-mail e comprovantes de posse")
    class TokenVerificacao {

        @ParameterizedTest(name = "aceita {0} por {1}")
        @CsvSource({
            "confirmar_email,   email,   cliente@teste.coe.local",
            "link_posse,        email,   cliente@teste.coe.local",
            "recuperar_senha,   email,   cliente@teste.coe.local",
            "comprovante_posse, email,   cliente@teste.coe.local",
            "comprovante_posse, celular, 47900000406"
        })
        void combinacoesValidas(String finalidade, String canal, String destino) {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> token(usuario, canal, destino, finalidade)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "barra {0}")
        @CsvSource(delimiter = '|', textBlock = """
                link por celular             | celular | 47900000407                | confirmar_email   | ck_token_verificacao_canal
                e-mail em maiúsculas         | email   | Cliente@teste.coe.local    | confirmar_email   | ck_token_verificacao_destino
                e-mail inválido              | email   | sem-arroba                 | confirmar_email   | ck_token_verificacao_destino
                celular com máscara          | celular | (47) 90000-0408            | comprovante_posse | ck_token_verificacao_destino
                finalidade fora da lista     | email   | cliente@teste.coe.local    | outra             | ck_token_verificacao_finalidade
                """)
        void barraInvalido(String caso, String canal, String destino, String finalidade, String constraint) {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> token(usuario, canal, destino, finalidade));

            assertConstraint(erro, CHECK_VIOLATION, constraint);
        }

        @Test
        @DisplayName("sem dono só para provar posse e para o comprovante")
        void semDono() {
            assertThatCode(() -> token(null, "email", "novo@teste.coe.local", "link_posse"))
                    .doesNotThrowAnyException();

            PSQLException erro = erroDoBanco(() -> token(null, "email", "novo@teste.coe.local", "confirmar_email"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_token_verificacao_dono");
        }

        @Test
        @DisplayName("o hash é único e tem 32 bytes")
        void hash() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "INSERT INTO token_verificacao (usuario_id, canal, destino, finalidade, token_hash, criado_em, expira_em)"
                            + " VALUES (?, 'email', 'a@teste.coe.local', 'confirmar_email', convert_to('curto', 'UTF8'),"
                            + " now(), now() + interval '1 day')",
                    usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_token_verificacao_hash");
        }

        @Test
        @DisplayName("só o uso muda, uma vez")
        void soOUsoMuda() {
            UUID token = token(fixtures.usuario(), "email", "b@teste.coe.local", "confirmar_email");
            fixtures.agirComoAplicacao();
            jdbc.update("UPDATE token_verificacao SET usado_em = now() WHERE id = ?", token);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE token_verificacao SET usado_em = now() + interval '1 second' WHERE id = ?", token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só o uso");
        }

        @Test
        @DisplayName("o destino não muda")
        void destinoImutavel() {
            UUID token = token(fixtures.usuario(), "email", "c@teste.coe.local", "confirmar_email");

            PSQLException erro = erroDoBanco(() ->
                    jdbc.update("UPDATE token_verificacao SET destino = 'outro@teste.coe.local' WHERE id = ?", token));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só o uso");
        }

        @Test
        @DisplayName("a aplicação não apaga nem esvazia")
        void aplicacaoNaoApaga() {
            UUID token = token(fixtures.usuario(), "email", "d@teste.coe.local", "confirmar_email");
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM token_verificacao WHERE id = ?", token));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        @Test
        @DisplayName("índices: dono (FK, completo) e destino + criado_em (limite de envios)")
        void indices() {
            assertThat(jdbc.queryForList(
                            "SELECT indexname FROM pg_indexes WHERE tablename = 'token_verificacao'", String.class))
                    .contains(
                            "ix_token_verificacao_usuario",
                            "ix_token_verificacao_destino",
                            "uq_token_verificacao_hash");
        }
    }

    @Test
    @DisplayName("refresh_token aceita o motivo contato_transferido (RN61)")
    void motivoContatoTransferido() {
        UUID usuario = fixtures.usuario();
        UUID token = jdbc.queryForObject("""
                INSERT INTO refresh_token (usuario_id, familia_id, token_hash, criado_em, expira_em, sessao_iniciada_em)
                VALUES (?, gen_random_uuid(), sha256(gen_random_uuid()::text::bytea), now(), now() + interval '1 day', now())
                RETURNING id""", UUID.class, usuario);

        assertThatCode(() -> jdbc.update(
                        "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = 'contato_transferido' WHERE id = ?",
                        token))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("revisão: invalidação, um ativo por destino, MFA com celular, UPDATE só nas colunas de uso")
    class Revisao {

        @Test
        @DisplayName("código invalidado não pode ser usado (os dois não andam juntos)")
        void codigoUsadoOuInvalidado() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            jdbc.update("UPDATE codigo_sms SET invalidado_em = now() WHERE id = ?", codigo);

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE codigo_sms SET usado_em = now() WHERE id = ?", codigo));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_uso_ou_invalidado");
        }

        @Test
        @DisplayName("invalidação não volta atrás")
        void invalidacaoNaoVoltaAtras() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            jdbc.update("UPDATE codigo_sms SET invalidado_em = now() WHERE id = ?", codigo);

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE codigo_sms SET invalidado_em = NULL WHERE id = ?", codigo));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só tentativas (subindo) e uso");
        }

        @Test
        @DisplayName("só um código ativo por celular e finalidade (dois envios ao mesmo tempo)")
        void umCodigoAtivo() {
            UUID usuario = fixtures.usuario();
            codigoSms(usuario, "login");

            PSQLException erro = erroDoBanco(() -> codigoSms(usuario, "login"));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_codigo_sms_ativo");
        }

        @Test
        @DisplayName("invalidando o anterior, o novo código entra")
        void novoDepoisDeInvalidar() {
            UUID usuario = fixtures.usuario();
            UUID anterior = codigoSms(usuario, "login");
            jdbc.update("UPDATE codigo_sms SET invalidado_em = now() WHERE id = ?", anterior);

            assertThatCode(() -> codigoSms(usuario, "login")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("código de MFA sempre tem desafio")
        void mfaSemDesafio() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> codigoSms(usuario, "mfa"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_codigo_sms_mfa_com_desafio");
        }

        @Test
        @DisplayName("só um link ativo por destino e finalidade")
        void umLinkAtivo() {
            UUID usuario = fixtures.usuario();
            token(usuario, "email", "e@teste.coe.local", "confirmar_email");

            PSQLException erro = erroDoBanco(() -> token(usuario, "email", "e@teste.coe.local", "confirmar_email"));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_token_verificacao_ativo");
        }

        @Test
        @DisplayName("link invalidado não pode ser usado")
        void linkUsadoOuInvalidado() {
            UUID token = token(fixtures.usuario(), "email", "f@teste.coe.local", "confirmar_email");
            jdbc.update("UPDATE token_verificacao SET invalidado_em = now() WHERE id = ?", token);

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE token_verificacao SET usado_em = now() WHERE id = ?", token));

            assertConstraint(erro, CHECK_VIOLATION, "ck_token_verificacao_uso_ou_invalidado");
        }

        @Test
        @DisplayName("destino com mais de 254 caracteres é recusado")
        void destinoLongo() {
            UUID usuario = fixtures.usuario();

            PSQLException erro =
                    erroDoBanco(() -> token(usuario, "email", "a".repeat(250) + "@teste.coe.local", "confirmar_email"));

            assertConstraint(erro, CHECK_VIOLATION, "ck_token_verificacao_destino");
        }

        @Test
        @DisplayName("MFA ligado exige celular confirmado (sem celular, o segundo passo não chega)")
        void mfaExigeCelularConfirmado() {
            UUID usuario = fixtures.usuario();

            PSQLException erro =
                    erroDoBanco(() -> jdbc.update("UPDATE usuario SET mfa_sms_ativo = true WHERE id = ?", usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_usuario_mfa_celular");
        }

        @Test
        @DisplayName("a aplicação só altera as colunas de uso do código")
        void aplicacaoSoAlteraUsoDoCodigo() {
            UUID codigo = codigoSms(fixtures.usuario(), "login");
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE codigo_sms SET celular = '47900000498' WHERE id = ?", codigo));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("permission denied");
        }

        @Test
        @DisplayName("a aplicação só altera as colunas de uso do link")
        void aplicacaoSoAlteraUsoDoLink() {
            UUID token = token(fixtures.usuario(), "email", "g@teste.coe.local", "confirmar_email");
            fixtures.agirComoAplicacao();

            PSQLException erro = erroDoBanco(() ->
                    jdbc.update("UPDATE token_verificacao SET destino = 'h@teste.coe.local' WHERE id = ?", token));

            assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(erro.getServerErrorMessage().getMessage()).contains("permission denied");
        }
    }
}
