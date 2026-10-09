package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/publico/regras: só comissão, prazo de liberação e quem paga a taxa, lidos de uma vez
 * do ConfiguracaoNegocio, para o front montar os textos sem número fixo.
 */
class RegrasPublicasTest extends BancoIntegracaoTest {

    private static final String URL = "/api/publico/regras";
    private static final String NOVO_VALOR = "INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) "
            + "VALUES (?, ?, ?, 'teste', now() - interval '1 minute')";

    @Autowired
    ConfiguracaoNegocio configuracao;

    @Autowired
    JsonMapper json;

    @BeforeEach
    @AfterEach
    void cacheLimpo() {
        configuracao.invalidarCache();
    }

    private Map<String, Object> regras() throws Exception {
        String corpo = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readValue(corpo, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    @DisplayName("200 sem token com os valores em vigor: comissão, prazo de liberação (ISO-8601) e quem paga")
    void valoresDoSeed() throws Exception {
        assertThat(regras())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("comissao", "0.1000", "prazoLiberacao", "PT12H", "taxaPagaPor", "CLIENTE"));
    }

    @Test
    @DisplayName("nova configuração + invalidar o cache muda a resposta")
    void mudaComAConfiguracao() throws Exception {
        jdbc.update(NOVO_VALOR, "COMISSAO", "0.125", "decimal");
        jdbc.update(NOVO_VALOR, "AUTO_LIBERA_HORAS", "24", "inteiro");
        jdbc.update(NOVO_VALOR, "TAXA_PAGA_POR", "profissional", "texto");
        configuracao.invalidarCache();

        assertThat(regras())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("comissao", "0.1250", "prazoLiberacao", "PT24H", "taxaPagaPor", "PROFISSIONAL"));
    }

    @Test
    @DisplayName("nenhum outro parâmetro sai (prazo de disputa, limite LC 150, versão dos termos...)")
    void soOsTres() throws Exception {
        assertThat(regras()).containsOnlyKeys("comissao", "prazoLiberacao", "taxaPagaPor");
    }

    @Test
    @DisplayName("só GET: POST exige login")
    void soGet() throws Exception {
        mockMvc.perform(post(URL).header("Origin", ORIGEM_DO_FRONT)).andExpect(status().isUnauthorized());
    }
}
