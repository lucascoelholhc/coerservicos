package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * DB-15: V13, teto absoluto da sessão (PA03, 90 dias desde o login). O início da sessão é gravado
 * no login, herdado pelo sucessor sem mudança e imutável; o banco recusa sucessor com outro início.
 */
class MigracaoV13Test extends BancoIntegracaoTest {

    private UUID refreshToken(UUID usuario, UUID familia, String inicio) {
        return jdbc.queryForObject(
                "INSERT INTO refresh_token (usuario_id, familia_id, token_hash, criado_em, expira_em, sessao_iniciada_em)"
                        + " VALUES (?, ?, sha256(gen_random_uuid()::text::bytea), now(), now() + interval '30 days', "
                        + inicio + ") RETURNING id",
                UUID.class,
                usuario,
                familia);
    }

    @Test
    @DisplayName("o início da sessão é obrigatório")
    void inicioObrigatorio() {
        UUID usuario = fixtures.usuario();

        PSQLException erro = erroDoBanco(() -> jdbc.update(
                "INSERT INTO refresh_token (usuario_id, familia_id, token_hash, criado_em, expira_em)"
                        + " VALUES (?, gen_random_uuid(), sha256('v13'::bytea), now(), now() + interval '1 day')",
                usuario));

        assertThat(erro.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
        assertThat(erro.getServerErrorMessage().getColumn()).isEqualTo("sessao_iniciada_em");
    }

    @Test
    @DisplayName("a sessão não começa depois do próprio token")
    void inicioNaoPassaDaCriacao() {
        UUID usuario = fixtures.usuario();

        PSQLException erro = erroDoBanco(() -> refreshToken(usuario, UUID.randomUUID(), "now() + interval '1 second'"));

        assertConstraint(erro, CHECK_VIOLATION, "ck_refresh_token_sessao");
    }

    @Test
    @DisplayName("o início da sessão não muda")
    void inicioImutavel() {
        UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID(), "now()");

        PSQLException erro = erroDoBanco(() -> jdbc.update(
                "UPDATE refresh_token SET sessao_iniciada_em = sessao_iniciada_em - interval '1 day' WHERE id = ?",
                token));

        assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só uso, revogação e sucessor");
    }

    @Test
    @DisplayName("sucessor com o mesmo início é aceito")
    void sucessorComMesmoInicio() {
        UUID usuario = fixtures.usuario();
        UUID familia = UUID.randomUUID();
        UUID antigo = refreshToken(usuario, familia, "now() - interval '10 days'");
        UUID sucessor = refreshToken(usuario, familia, "now() - interval '10 days'");

        assertThatCode(() -> jdbc.update(
                        "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?",
                        sucessor,
                        antigo))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sucessor com outro início é recusado pelo banco (a sessão não se estica)")
    void sucessorComOutroInicio() {
        UUID usuario = fixtures.usuario();
        UUID familia = UUID.randomUUID();
        UUID antigo = refreshToken(usuario, familia, "now() - interval '10 days'");
        UUID esticado = refreshToken(usuario, familia, "now()");

        PSQLException erro = erroDoBanco(() -> jdbc.update(
                "UPDATE refresh_token SET usado_em = now(), substituido_por = ? WHERE id = ?", esticado, antigo));

        assertConstraint(erro, FOREIGN_KEY_VIOLATION, "fk_refresh_token_sucessor");
    }

    @Test
    @DisplayName("a aplicação revoga por teto")
    void motivoTeto() {
        UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID(), "now()");
        fixtures.agirComoAplicacao();

        assertThatCode(() -> jdbc.update(
                        "UPDATE refresh_token SET revogado_em = now(), motivo_revogacao = 'teto' WHERE id = ?", token))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a FK nova do sucessor tem índice não parcial começando por substituido_por")
    void indiceDaFk() {
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pg_index i
                         WHERE i.indrelid = 'public.refresh_token'::regclass AND i.indpred IS NULL
                           AND i.indkey[0] = (SELECT attnum FROM pg_attribute
                                               WHERE attrelid = i.indrelid AND attname = 'substituido_por')""", Integer.class)).isPositive();
    }

    @Test
    @DisplayName("a aplicação também não altera o início da sessão")
    void aplicacaoNaoAlteraInicio() {
        UUID token = refreshToken(fixtures.usuario(), UUID.randomUUID(), "now()");
        fixtures.agirComoAplicacao();

        PSQLException erro = erroDoBanco(() -> jdbc.update(
                "UPDATE refresh_token SET sessao_iniciada_em = sessao_iniciada_em - interval '1 day' WHERE id = ?",
                token));

        assertThat(erro.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
}
