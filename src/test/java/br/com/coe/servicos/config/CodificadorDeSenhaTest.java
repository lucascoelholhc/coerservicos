package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CodificadorDeSenhaTest {

    private final PasswordEncoder codificador = new SegurancaConfig().codificadorDeSenha();

    @Test
    @DisplayName("grava com {bcrypt} e custo 12, nunca a senha em claro")
    void bcryptCusto12() {
        String hash = codificador.encode("Casa-Azul-2026");

        assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain("Casa-Azul-2026");
        assertThat(codificador.matches("Casa-Azul-2026", hash)).isTrue();
        assertThat(codificador.matches("outra-senha", hash)).isFalse();
    }

    @Test
    @DisplayName("aceita o hash sem prefixo do seed local ($2b$) como bcrypt, para o login dos usuários de teste")
    void hashSemPrefixoDoSeed() {
        String hashDoSeed = "$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y";

        assertThat(codificador.matches("coe-local-123", hashDoSeed)).isTrue();
    }

    @Test
    @DisplayName("hash nulo, vazio ou de outro algoritmo nunca confere")
    void hashInvalidoNaoConfere() {
        assertThat(codificador.matches("coe-local-123", null)).isFalse();
        assertThat(codificador.matches("coe-local-123", "")).isFalse();
        assertThat(codificador.matches("coe-local-123", "{noop}coe-local-123")).isFalse();
    }
}
