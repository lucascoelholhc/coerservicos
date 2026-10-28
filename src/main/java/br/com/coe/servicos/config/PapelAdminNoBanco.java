package br.com.coe.servicos.config;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;

/**
 * Painel do admin só para quem é ADMIN no banco agora (DOM-11): o token de acesso vale até 15 min e quem foi tirado
 * da Equipe ainda teria o papel nele. Registrado só em {@code /api/admin/**}; o {@code @PreAuthorize} continua
 * valendo antes (sem o papel no token nem chega aqui).
 */
class PapelAdminNoBanco implements HandlerInterceptor {

    private final JdbcTemplate jdbc;

    PapelAdminNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object alvo) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (alvo instanceof HandlerMethod && autenticacao instanceof JwtAuthenticationToken token) {
            UUID id = UUID.fromString(token.getToken().getSubject());
            Boolean admin = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM usuario_papel WHERE usuario_id = ? AND papel = 'ADMIN')",
                    Boolean.class,
                    id);
            if (!Boolean.TRUE.equals(admin)) {
                throw new AcaoProibidaException("fora-da-equipe", "Você não faz mais parte da equipe.");
            }
        }
        return true;
    }
}
