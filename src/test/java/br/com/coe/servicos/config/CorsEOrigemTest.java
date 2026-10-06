package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;

/** CORE-08: CORS só para as origens configuradas; Origin obrigatório nas rotas do cookie. */
class CorsEOrigemTest extends IntegracaoTest {

    private static final String ORIGEM_ESTRANHA = "https://malicioso.example";
    /** Hash do seed local (bcrypt $2b$ custo 10) da senha coe-local-123. */
    private static final String HASH_DO_SEED = "$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y";

    private static final AtomicLong SEQUENCIA = new AtomicLong(8_000);

    @Autowired
    JdbcTemplate jdbc;

    record Sessao(UUID usuarioId, String access, String refresh) {}

    /** Cria uma conta direto no banco (celular fictício, senha do seed local) e entra. */
    private Sessao entrar() throws Exception {
        long numero = SEQUENCIA.incrementAndGet();
        String celular = String.valueOf(47_900_000_000L + numero);
        UUID id = jdbc.queryForObject(
                "INSERT INTO usuario (nome, celular, email, senha_hash) VALUES ('Pessoa Origem', ?, ?, ?) RETURNING id",
                UUID.class,
                celular,
                "origem" + numero + "@teste.coe.local",
                HASH_DO_SEED);
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'CLIENTE')", id);
        MockHttpServletResponse resposta = mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + celular + "\",\"senha\":\"coe-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        String cookie = resposta.getHeader("Set-Cookie");
        return new Sessao(
                id,
                JsonPath.read(resposta.getContentAsString(), "$.accessToken"),
                cookie.substring("coe_refresh=".length(), cookie.indexOf(';')));
    }

    private int tokensUsados(UUID usuario) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND (usado_em IS NOT NULL OR revogado_em IS NOT NULL)",
                Integer.class,
                usuario);
    }

    private void origemRecusada(MockHttpServletRequestBuilder pedido) throws Exception {
        MockHttpServletResponse resposta = mockMvc.perform(pedido)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:origem-nao-permitida"))
                .andReturn()
                .getResponse();
        assertThat(resposta.getHeader("Set-Cookie")).as("não mexe no cookie").isNull();
    }

    @Test
    @DisplayName("preflight da origem do front no /api/auth: liberado, com credenciais")
    void preflightAuth() throws Exception {
        mockMvc.perform(options("/api/auth/renovar")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_DO_FRONT))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("preflight da origem do front no resto da API: liberado, sem credenciais")
    void preflightApi() throws Exception {
        mockMvc.perform(options("/api/contas/eu")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_DO_FRONT))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("origem fora da lista não recebe cabeçalho CORS nenhum")
    void origemEstranhaSemCors() throws Exception {
        mockMvc.perform(options("/api/auth/renovar")
                        .header("Origin", ORIGEM_ESTRANHA)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    /** Status da resposta, ou -1 se o firewall do Spring Security recusou antes (exceção). */
    private int statusSemOrigem(String caminho, Cookie cookie) {
        try {
            return mockMvc.perform(post(caminho).cookie(cookie))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception recusadaPeloFirewall) {
            return -1;
        }
    }

    @Test
    @DisplayName("variações do caminho (barra no fim, ;parametro) não escapam da checagem de Origin")
    void variacoesDoCaminho() throws Exception {
        Sessao sessao = entrar();
        Cookie cookie = new Cookie("coe_refresh", sessao.refresh());

        assertThat(statusSemOrigem("/api/auth/renovar/", cookie)).isNotEqualTo(200);
        assertThat(statusSemOrigem("/api/auth/renovar;a=b", cookie)).isNotEqualTo(200);
        assertThat(statusSemOrigem("/api/auth/RENOVAR", cookie)).isNotEqualTo(200);
        assertThat(tokensUsados(sessao.usuarioId()))
                .as("o refresh continua intacto")
                .isZero();
    }

    @Test
    @DisplayName("todo POST em /api/auth/ menos o entrar exige Origin (falha fechada)")
    void sairDeTodosExigeOrigem() throws Exception {
        Sessao sessao = entrar();

        origemRecusada(post("/api/auth/sair-de-todos").header("Authorization", "Bearer " + sessao.access()));
        assertThat(tokensUsados(sessao.usuarioId())).isZero();
    }

    @Test
    @DisplayName("renovar: Origin certo renova; errado ou ausente dá 403 e não toca no refresh")
    void origemNoRenovar() throws Exception {
        Sessao sessao = entrar();
        Cookie cookie = new Cookie("coe_refresh", sessao.refresh());

        origemRecusada(
                post("/api/auth/renovar").header("Origin", ORIGEM_ESTRANHA).cookie(cookie));
        origemRecusada(post("/api/auth/renovar").cookie(cookie));
        assertThat(tokensUsados(sessao.usuarioId()))
                .as("o refresh continua intacto")
                .isZero();

        mockMvc.perform(post("/api/auth/renovar")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sair: Origin certo sai; errado ou ausente dá 403 e a sessão continua")
    void origemNoSair() throws Exception {
        Sessao sessao = entrar();
        Cookie cookie = new Cookie("coe_refresh", sessao.refresh());
        String bearer = "Bearer " + sessao.access();

        origemRecusada(post("/api/auth/sair")
                .header("Authorization", bearer)
                .header("Origin", ORIGEM_ESTRANHA)
                .cookie(cookie));
        origemRecusada(post("/api/auth/sair").header("Authorization", bearer).cookie(cookie));
        assertThat(tokensUsados(sessao.usuarioId())).as("a sessão continua").isZero();

        mockMvc.perform(post("/api/auth/sair")
                        .header("Authorization", bearer)
                        .header("Origin", ORIGEM_DO_FRONT)
                        .cookie(cookie))
                .andExpect(status().isNoContent());
        assertThat(tokensUsados(sessao.usuarioId())).isOne();
    }
}
