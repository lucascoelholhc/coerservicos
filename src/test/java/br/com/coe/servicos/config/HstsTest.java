package br.com.coe.servicos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import br.com.coe.servicos.IntegracaoTest;

/** Com coe.seguranca.hsts=true (só o application-prod.yml liga), HTTPS recebe HSTS. */
@TestPropertySource(properties = "coe.seguranca.hsts=true")
class HstsTest extends IntegracaoTest {

    @Test
    @DisplayName("HSTS de 1 ano com subdomínios")
    void comHsts() throws Exception {
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", Matchers.containsString("includeSubDomains")));
    }
}
