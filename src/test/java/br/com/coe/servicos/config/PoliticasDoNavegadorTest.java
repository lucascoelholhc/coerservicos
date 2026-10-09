package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.Papel;

/**
 * FE-01: Content-Security-Policy e Permissions-Policy em toda resposta, com exatamente o mesmo texto
 * do front (frontend/cabecalhos-seguranca.json, que o vite preview usa).
 */
class PoliticasDoNavegadorTest extends IntegracaoTest {

    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    private static final String PERMISSOES = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    /** O Maven roda os testes na raiz do projeto. */
    private static final Path ARQUIVO_DO_FRONT = Path.of("frontend", "cabecalhos-seguranca.json");

    private static void politicas(ResultActions resposta) throws Exception {
        resposta.andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("Permissions-Policy", PERMISSOES));
    }

    @Test
    @DisplayName("rota pública, rota protegida com login e 401 sem login: CSP e Permissions-Policy exatos")
    void emTodaResposta() throws Exception {
        politicas(mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()));
        politicas(mockMvc.perform(
                        get("/api/contas/eu").header("Authorization", bearer(UUID.randomUUID(), Papel.CLIENTE)))
                .andExpect(status().isNotFound())); // passou do login e chegou ao serviço (conta inexistente)
        politicas(mockMvc.perform(get("/api/contas/eu")).andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("o backend e o vite preview usam o mesmo texto (falha se o arquivo do front não existir)")
    void mesmoTextoDoFront() throws Exception {
        assertThat(ARQUIVO_DO_FRONT)
                .as("frontend/cabecalhos-seguranca.json é a fonte única dos cabeçalhos do front; não pode faltar")
                .isRegularFile();

        Map<String, String> doFront = JsonMapper.builder()
                .build()
                .readValue(Files.readString(ARQUIVO_DO_FRONT), new TypeReference<Map<String, String>>() {});

        assertThat(doFront)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "Content-Security-Policy", SegurancaConfig.CONTENT_SECURITY_POLICY,
                        "Permissions-Policy", SegurancaConfig.PERMISSIONS_POLICY));
        assertThat(SegurancaConfig.CONTENT_SECURITY_POLICY).isEqualTo(CSP);
        assertThat(SegurancaConfig.PERMISSIONS_POLICY).isEqualTo(PERMISSOES);
    }
}
