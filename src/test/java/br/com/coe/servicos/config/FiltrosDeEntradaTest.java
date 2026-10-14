package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;
import br.com.coe.servicos.compartilhado.limite.Balde;
import br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa;
import br.com.coe.servicos.compartilhado.limite.LimitadorEmMemoria;
import br.com.coe.servicos.compartilhado.limite.PropriedadesDeLimites;

/** CORE-07: respostas dos filtros de entrada sem subir a aplicação (falhas difíceis de provocar). */
class FiltrosDeEntradaTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private static MockHttpServletRequest pedirCodigo() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", "/api/auth/codigo");
        requisicao.setRemoteAddr("203.0.113.30");
        return requisicao;
    }

    @Nested
    @DisplayName("FiltroDeLimites: falha do Postgres recusa (fail-closed)")
    class FalhaDoLimitador {

        private final LimitadorEmMemoria memoria = new LimitadorEmMemoria(
                new PropriedadesDeLimites(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(1),
                        1,
                        Duration.ofMillis(500),
                        Map.of(
                                "api-ip", new PropriedadesDeLimites.Regra(1000, Duration.ofMinutes(1), 1),
                                "publico-ip", new PropriedadesDeLimites.Regra(1000, Duration.ofMinutes(1), 1))),
                java.time.Clock.systemUTC());

        @Test
        @DisplayName("banco fora = 503 em Problem Details em pt-BR e a requisição não segue")
        void bancoFora() throws ServletException, IOException {
            LimitadorDeTaxa limitador = mock(LimitadorDeTaxa.class);
            doThrow(new ServicoInstavelException("fora", new RuntimeException()))
                    .when(limitador)
                    .consumir(any(Balde[].class));
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            new FiltroDeLimites(limitador, memoria, json).doFilter(pedirCodigo(), resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(503);
            assertThat(resposta.getContentType()).isEqualTo("application/problem+json");
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .contains("urn:coe:erro:servico-instavel")
                    .contains("O serviço está instável. Tente de novo em instantes.");
            assertThat(cadeia.getRequest()).isNull();
        }

        @Test
        @DisplayName("trava disputada = 429 com Retry-After curto e a requisição não segue")
        void travaDisputada() throws ServletException, IOException {
            LimitadorDeTaxa limitador = mock(LimitadorDeTaxa.class);
            doThrow(new MuitasTentativasException("Muitas tentativas.", Duration.ofSeconds(1)))
                    .when(limitador)
                    .consumir(any(Balde[].class));
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            new FiltroDeLimites(limitador, memoria, json).doFilter(pedirCodigo(), resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(429);
            assertThat(resposta.getHeader("Retry-After")).isEqualTo("1");
            assertThat(cadeia.getRequest()).isNull();
        }
    }

    @Nested
    @DisplayName("FiltroDeTamanhoDoCorpo: 413 acima do limite")
    class TamanhoDoCorpo {

        private final FiltroDeTamanhoDoCorpo filtro = new FiltroDeTamanhoDoCorpo(16, json);

        @Test
        @DisplayName("Content-Length acima do limite = 413 sem ler o corpo")
        void tamanhoDeclarado() throws ServletException, IOException {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", "/api/contas/cliente");
            requisicao.setContent(new byte[17]);
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(requisicao, resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(413);
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .contains("urn:coe:erro:conteudo-muito-grande");
            assertThat(cadeia.getRequest()).isNull();
        }

        @Test
        @DisplayName("sem Content-Length (chunked) e acima do limite = 413")
        void semTamanhoDeclarado() throws ServletException, IOException {
            MockHttpServletRequest requisicao = semContentLength(new byte[17]);
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(requisicao, resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(413);
            assertThat(cadeia.getRequest()).isNull();
        }

        @Test
        @DisplayName("sem Content-Length e dentro do limite: segue com o corpo inteiro")
        void semTamanhoDeclaradoDentroDoLimite() throws ServletException, IOException {
            MockHttpServletRequest requisicao = semContentLength("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);

            assertThat(cadeia.getRequest()).isNotNull();
            assertThat(cadeia.getRequest().getInputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("GET e rotas fora de /api seguem sem conferir")
        void foraDoEscopo() throws ServletException, IOException {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/publico/regras");
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);

            assertThat(cadeia.getRequest()).isNotNull();
        }

        private MockHttpServletRequest semContentLength(byte[] corpo) {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", "/api/contas/cliente") {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
            requisicao.setContent(corpo);
            return requisicao;
        }
    }
}
