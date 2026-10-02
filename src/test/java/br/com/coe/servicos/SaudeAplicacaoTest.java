package br.com.coe.servicos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Teste de fumaça: a aplicação sobe com banco real e responde saudável. */
class SaudeAplicacaoTest extends IntegracaoTest {

    @Test
    @DisplayName("GET /actuator/health responde UP sem login")
    void healthRespondeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
