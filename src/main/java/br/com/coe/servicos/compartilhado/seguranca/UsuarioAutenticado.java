package br.com.coe.servicos.compartilhado.seguranca;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Quem está chamando, sempre a partir do token (o sub do JWT). Nunca use um id vindo do corpo ou da
 * URL para decidir de quem é o recurso (anti-IDOR, CORE-06).
 */
@Component
public class UsuarioAutenticado {

    public UUID id() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken token) {
            return UUID.fromString(token.getToken().getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Sem usuário autenticado");
    }
}
