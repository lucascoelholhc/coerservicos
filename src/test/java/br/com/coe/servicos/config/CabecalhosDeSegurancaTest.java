package br.com.coe.servicos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;

/** CORE-08: headers de segurança em toda resposta; HSTS só no perfil prod. */
class CabecalhosDeSegurancaTest extends IntegracaoTest {

    private static void basicos(ResultActions resposta) throws Exception {
        resposta.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("resposta pública, protegida e de erro: nosniff, DENY e no-referrer")
    void emTodaResposta() throws Exception {
        basicos(mockMvc.perform(get("/actuator/health")));
        basicos(mockMvc.perform(get("/api/qualquer-coisa")));
        basicos(mockMvc.perform(
                post("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    @Test
    @DisplayName("sem cache nas rotas de autenticação")
    void semCacheNoAuth() throws Exception {
        mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("fora do prod não há HSTS, nem em HTTPS")
    void semHstsForaDoProd() throws Exception {
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
