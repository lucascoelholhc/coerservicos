package br.com.coe.servicos.config;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.coe.servicos.compartilhado.log.Correlacao;

/**
 * O primeiro filtro da cadeia (CORE-11): gera o id da requisição, põe no MDC e devolve no header
 * X-Request-Id antes de qualquer outro filtro responder (limites, tamanho, Security). O X-Request-Id que
 * vier do cliente é ignorado: um id escolhido de fora poderia misturar logs de requisições diferentes.
 * O MDC é limpo no fim, para não vazar para a próxima requisição na mesma thread.
 */
final class FiltroDeCorrelacao extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        MDC.put(Correlacao.CHAVE, id);
        resposta.setHeader(Correlacao.HEADER, id);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            MDC.remove(Correlacao.CHAVE);
        }
    }
}
