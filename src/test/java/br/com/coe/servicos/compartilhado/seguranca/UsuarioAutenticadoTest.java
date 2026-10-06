package br.com.coe.servicos.compartilhado.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UsuarioAutenticadoTest {

    private final UsuarioAutenticado usuario = new UsuarioAutenticado();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarCom(String sub) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(sub)
                .issuedAt(Instant.parse("2026-10-06T12:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void idDoToken() {
        UUID id = UUID.randomUUID();
        autenticarCom(id.toString());

        assertThat(usuario.id()).isEqualTo(id);
    }

    @Test
    void semAutenticacao() {
        assertThatThrownBy(usuario::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void subQueNaoEhUuid() {
        autenticarCom("47900000001");

        assertThatThrownBy(usuario::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
