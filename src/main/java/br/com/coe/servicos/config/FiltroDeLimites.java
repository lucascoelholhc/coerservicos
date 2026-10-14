package br.com.coe.servicos.config;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;
import br.com.coe.servicos.compartilhado.limite.Balde;
import br.com.coe.servicos.compartilhado.limite.ChaveDeIp;
import br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa;
import br.com.coe.servicos.compartilhado.limite.LimitadorEmMemoria;

/**
 * Limites por IP (CORE-07), antes do Spring Security: a recusa vem antes do BCrypt e antes de ler o
 * corpo. Em memória: teto geral de /api/** e GETs públicos. No Postgres: as rotas de
 * {@link RotasLimitadas}. O IP é o {@code getRemoteAddr()}: no prod o RemoteIpValve já o trocou pelo IP
 * real vindo do ALB; fora dele, X-Forwarded-For é ignorado.
 */
final class FiltroDeLimites extends OncePerRequestFilter {

    private static final String PREFIXO_DA_API = "/api/";
    private static final String PREFIXO_PUBLICO = "/api/publico/";

    private final LimitadorDeTaxa limitador;
    private final LimitadorEmMemoria memoria;
    private final JsonMapper json;

    FiltroDeLimites(LimitadorDeTaxa limitador, LimitadorEmMemoria memoria, JsonMapper json) {
        this.limitador = limitador;
        this.memoria = memoria;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return !caminho(requisicao).toLowerCase(java.util.Locale.ROOT).startsWith(PREFIXO_DA_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String caminho = caminho(requisicao);
        String ip = ChaveDeIp.de(requisicao.getRemoteAddr());
        try {
            memoria.consumir(Balde.porChave("api-ip", ip));
            if ("GET".equalsIgnoreCase(requisicao.getMethod())
                    && caminho.toLowerCase(java.util.Locale.ROOT).startsWith(PREFIXO_PUBLICO)) {
                memoria.consumir(Balde.porChave("publico-ip", ip));
            }
            Optional<RotasLimitadas.RotaLimitada> rota = RotasLimitadas.buscar(requisicao.getMethod(), caminho);
            if (rota.isPresent()) {
                limitador.consumir(rota.get().cobrancas().stream()
                        .map(cobranca -> cobranca.balde(ip))
                        .toArray(Balde[]::new));
            }
        } catch (MuitasTentativasException recusa) {
            resposta.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(recusa.esperaEmSegundos()));
            RespostaDeProblema.escrever(
                    resposta,
                    json,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "muitas-tentativas",
                    "Muitas tentativas",
                    recusa.getMessage());
            return;
        } catch (ServicoInstavelException fora) {
            resposta.setHeader(HttpHeaders.RETRY_AFTER, "5");
            RespostaDeProblema.escrever(
                    resposta,
                    json,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "servico-instavel",
                    "Serviço instável",
                    "O serviço está instável. Tente de novo em instantes.");
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private static String caminho(HttpServletRequest requisicao) {
        return CaminhoDaRequisicao.de(requisicao);
    }
}
