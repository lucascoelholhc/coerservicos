package br.com.coe.servicos.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Todo POST em /api/auth/ (onde vive o cookie do refresh), menos o entrar, só aceita Origin da lista
 * (CORE-08, contra CSRF). Falha fechada: qualquer variação do caminho também é checada. Ausente ou
 * estranha: 403 antes de qualquer coisa, sem tocar no refresh nem no cookie.
 */
final class FiltroDeOrigem extends OncePerRequestFilter {

    private static final String PREFIXO_DO_COOKIE = "/api/auth/";
    private static final String ENTRAR = "/api/auth/entrar";

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
        boolean postNoAuth = "POST".equalsIgnoreCase(requisicao.getMethod())
                && caminho.toLowerCase(java.util.Locale.ROOT).startsWith(PREFIXO_DO_COOKIE);
        return !postNoAuth || ENTRAR.equals(caminho);
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
