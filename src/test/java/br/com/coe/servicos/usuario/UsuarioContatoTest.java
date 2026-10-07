package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Perder um contato (RN61): o celular leva junto a confirmação e o MFA; o e-mail não mexe no MFA. */
class UsuarioContatoTest {

    private static final Instant AGORA = Instant.parse("2026-10-07T12:00:00Z");

    private static Usuario comMfa() {
        Usuario usuario = Usuario.novoCliente("Ana Silva", "47900000101", "ana@teste.coe.local", "hash", "89010000");
        usuario.confirmarCelular(AGORA);
        usuario.confirmarEmail(AGORA);
        usuario.definirMfa(true);
        return usuario;
    }

    @Test
    @DisplayName("perder o celular: zera o celular, a confirmação dele e o MFA; marca contato pendente")
    void perderCelular() {
        Usuario usuario = comMfa();

        usuario.perderCelular();

        assertThat(usuario.getCelular()).isNull();
        assertThat(usuario.isCelularConfirmado()).isFalse();
        assertThat(usuario.isMfaSmsAtivo()).isFalse();
        assertThat(usuario.isContatoPendente()).isTrue();
    }

    @Test
    @DisplayName("perder o e-mail: zera o e-mail e a confirmação dele; o MFA continua")
    void perderEmail() {
        Usuario usuario = comMfa();

        usuario.perderEmail();

        assertThat(usuario.getEmail()).isNull();
        assertThat(usuario.isEmailConfirmado()).isFalse();
        assertThat(usuario.isMfaSmsAtivo()).isTrue();
        assertThat(usuario.isContatoPendente()).isTrue();
    }

    @Test
    @DisplayName("com celular e e-mail, não há contato pendente")
    void semPendencia() {
        assertThat(comMfa().isContatoPendente()).isFalse();
    }
}
