package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.limite.LimpezaDeBaldes;

/**
 * CORE-07: limites por IP e globais aplicados por rota num filtro antes do Spring Security (recusa
 * antes do BCrypt e antes de ler o corpo). No perfil test o IP é o da conexão: X-Forwarded-For não
 * muda o balde (só o prod confia no ALB).
 */
class FiltroDeLimitesTest extends IntegracaoTest {

    private static final String IP = "203.0.113.20";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LimpezaDeBaldes limpeza;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    private static RequestPostProcessor doIp(String ip) {
        return requisicao -> {
            requisicao.setRemoteAddr(ip);
            return requisicao;
        };
    }

    private ResultActions pedirCodigo(String ip, String encaminhadoPor) throws Exception {
        var pedido = post("/api/auth/codigo")
                .with(doIp(ip))
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"47900000970\"}");
        if (encaminhadoPor != null) {
            pedido.header("X-Forwarded-For", encaminhadoPor);
        }
        return mockMvc.perform(pedido);
    }

    @Test
    @DisplayName("o 11º pedido de código do mesmo IP na hora = 429 em Problem Details com Retry-After")
    void limitePorIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedirCodigo(IP, null).andExpect(status().isAccepted());
        }

        pedirCodigo(IP, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Aguarde um pouco e tente de novo."))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
    }

    @Test
    @DisplayName("caminho com percent-encoding (%63odigo, %61pi) cai no mesmo balde: não pula o limite")
    void caminhoCodificado() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedirCodigo(IP, null).andExpect(status().isAccepted());
        }

        for (String caminho : java.util.List.of("/api/auth/%63odigo", "/%61pi/auth/codigo")) {
            mockMvc.perform(post(java.net.URI.create(caminho))
                            .with(doIp(IP))
                            .header("Origin", ORIGEM_DO_FRONT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"celular\":\"47900000970\"}"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Test
    @DisplayName("corpo grande em caminho codificado também dá 413")
    void corpoGrandeEmCaminhoCodificado() throws Exception {
        mockMvc.perform(post(java.net.URI.create("/api/contas/%63liente"))
                        .with(doIp("203.0.113.22"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "x".repeat(70 * 1024) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("outro IP tem o próprio balde")
    void outroIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedirCodigo(IP, null);
        }

        pedirCodigo("203.0.113.21", null).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("X-Forwarded-For forjado não muda o balde fora do prod")
    void encaminhamentoForjado() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedirCodigo(IP, "198.51.100." + i).andExpect(status().isAccepted());
        }

        pedirCodigo(IP, "198.51.100.99").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("GET público: o 61º do mesmo IP no minuto = 429 (balde em memória)")
    void publicoEmMemoria() throws Exception {
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/publico/regras").with(doIp(IP))).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/publico/regras").with(doIp(IP)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM limite_balde", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("teto geral de /api/** por IP: o 301º no minuto = 429, antes do Spring Security")
    void tetoGeral() throws Exception {
        for (int i = 0; i < 300; i++) {
            mockMvc.perform(get("/api/contas/eu").with(doIp(IP))).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/contas/eu").with(doIp(IP))).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("o relógio repõe o balde do IP depois da janela")
    void reposicao() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedirCodigo(IP, null);
        }
        relogio.avancar(java.time.Duration.ofHours(1));

        pedirCodigo(IP, null).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("a limpeza apaga os baldes vencidos e mantém os que valem")
    void limpezaDosVencidos() throws Exception {
        pedirCodigo(IP, null);
        jdbc.update("INSERT INTO limite_balde (chave, estado, expira_em) VALUES ('velho', ''::bytea, 1)");

        limpeza.executar();

        assertThat(jdbc.queryForList("SELECT chave FROM limite_balde", String.class))
                .doesNotContain("velho")
                .isNotEmpty();
    }

    @Test
    @DisplayName("toda rota limitada existe num controller (sem erro de digitação que deixe a rota sem limite)")
    void rotasExistem() {
        var rotasDosControllers = mapeamentos.getHandlerMethods().entrySet().stream()
                .filter(par -> par.getValue() instanceof HandlerMethod)
                .flatMap(par -> rotasDe(par.getKey()))
                .toList();

        assertThat(RotasLimitadas.ROTAS)
                .allSatisfy(rota -> assertThat(rotasDosControllers)
                        .as(rota.metodo() + " " + rota.caminho())
                        .contains(rota.metodo() + " " + rota.caminho()));
    }

    private static java.util.stream.Stream<String> rotasDe(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .flatMap(metodo -> info.getPatternValues().stream()
                        .map(caminho -> HttpMethod.valueOf(metodo.name()) + " " + caminho));
    }
}
