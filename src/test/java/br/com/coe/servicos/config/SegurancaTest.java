package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import br.com.coe.servicos.IntegracaoTest;

/** Primeiro SecurityFilterChain (CORE-02): tudo exige login, menos o cadastro e o health. */
class SegurancaTest extends IntegracaoTest {

    @Test
    @DisplayName("rota protegida sem login: 401 em Problem Details, em português")
    void rotaProtegidaSemLogin() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-autenticado"))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Entre na sua conta para continuar."));
    }

    @Test
    @DisplayName("o cadastro de cliente só é público no POST")
    void cadastroSoNoPost() throws Exception {
        mockMvc.perform(get("/api/contas/cliente")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login e renovação só são públicos no POST; token inválido dá 401 em Problem Details")
    void rotasDeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/auth/entrar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/renovar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/qualquer-coisa").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-autenticado"));
    }

    @Test
    @DisplayName("health continua aberto")
    void healthAberto() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Autowired
    ApplicationContext contexto;

    @Test
    @DisplayName("sem usuário em memória com senha gerada pelo Spring (o login é o do CORE-03)")
    void semUsuarioEmMemoria() {
        assertThat(contexto.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
