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

            org.slf4j.MDC.put("requisicao", "id-da-requisicao");
            try {
                new FiltroDeLimites(limitador, memoria, json).doFilter(pedirCodigo(), resposta, cadeia);
            } finally {
                org.slf4j.MDC.remove("requisicao");
            }

            assertThat(resposta.getStatus()).isEqualTo(503);
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"requisicao\":\"id-da-requisicao\""); // CORE-11
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

        private static final String ROTA_DE_UPLOAD = "/api/teste/foto";

        private final FiltroDeTamanhoDoCorpo filtro =
                new FiltroDeTamanhoDoCorpo(16, 64, java.util.Set.of(ROTA_DE_UPLOAD), json);

        @Test
        @DisplayName("rota de upload com multipart: vale o limite do upload, não o de 64 KB (CORE-09)")
        void uploadDentroDoLimiteProprio() throws ServletException, IOException {
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(multipart(ROTA_DE_UPLOAD, 40), new MockHttpServletResponse(), cadeia);

            assertThat(cadeia.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("rota de upload acima do limite do upload: 413 arquivo-grande-demais")
        void uploadAcimaDoLimiteProprio() throws ServletException, IOException {
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(multipart(ROTA_DE_UPLOAD, 65), resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(413);
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .contains("urn:coe:erro:arquivo-grande-demais");
            assertThat(cadeia.getRequest()).isNull();
        }

        @Test
        @DisplayName("multipart fora das rotas de upload, ou JSON na rota de upload: limite normal")
        void multipartSoNasRotasDeUpload() throws ServletException, IOException {
            MockHttpServletResponse foraDaRota = new MockHttpServletResponse();
            filtro.doFilter(multipart("/api/contas/cliente", 40), foraDaRota, new MockFilterChain());

            MockHttpServletRequest json = new MockHttpServletRequest("POST", ROTA_DE_UPLOAD);
            json.setContentType("application/json");
            json.setContent(new byte[40]);
            MockHttpServletResponse jsonNaRota = new MockHttpServletResponse();
            filtro.doFilter(json, jsonNaRota, new MockFilterChain());

            assertThat(foraDaRota.getStatus()).isEqualTo(413);
            assertThat(jsonNaRota.getStatus()).isEqualTo(413);
        }

        @Test
        @DisplayName("rota de upload sem Content-Length (chunked): 411, sem ler o corpo em memória")
        void uploadSemTamanho() throws ServletException, IOException {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", ROTA_DE_UPLOAD) {
                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
            requisicao.setContentType("multipart/form-data; boundary=xyz");
            requisicao.setContent(new byte[40]);
            MockHttpServletResponse resposta = new MockHttpServletResponse();
            MockFilterChain cadeia = new MockFilterChain();

            filtro.doFilter(requisicao, resposta, cadeia);

            assertThat(resposta.getStatus()).isEqualTo(411);
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .contains("urn:coe:erro:tamanho-nao-informado");
            assertThat(cadeia.getRequest()).isNull();
        }

        private MockHttpServletRequest multipart(String caminho, int tamanho) {
            MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", caminho);
            requisicao.setContentType("multipart/form-data; boundary=xyz");
            requisicao.setContent(new byte[tamanho]);
            return requisicao;
        }

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
