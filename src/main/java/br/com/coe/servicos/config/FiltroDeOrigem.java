package br.com.coe.servicos.config;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Rotas que usam o cookie do refresh (renovar e sair) só aceitam Origin da lista (CORE-08, contra
 * CSRF). Ausente ou estranha: 403 antes de qualquer coisa, sem tocar no refresh nem no cookie.
 */
final class FiltroDeOrigem extends OncePerRequestFilter {

    static final Set<String> ROTAS_COM_COOKIE = Set.of("/api/auth/renovar", "/api/auth/sair");

    private final OrigensPermitidas origens;
    private final JsonMapper json;

    FiltroDeOrigem(OrigensPermitidas origens, JsonMapper json) {
        this.origens = origens;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        String caminho =
                requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        return !"POST".equals(requisicao.getMethod()) || !ROTAS_COM_COOKIE.contains(caminho);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        if (origens.permite(requisicao.getHeader(HttpHeaders.ORIGIN))) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }
        RespostaDeProblema.escrever(
                resposta,
                json,
                HttpServletResponse.SC_FORBIDDEN,
                "origem-nao-permitida",
                "Acesso negado",
                "Esta requisição não veio do aplicativo da COE.");
    }
}
