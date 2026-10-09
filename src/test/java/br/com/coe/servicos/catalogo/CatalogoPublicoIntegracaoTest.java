package br.com.coe.servicos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.IntegracaoTest;

/**
 * GET /api/publico/catalogo (DOM-01): áreas e profissões ativas na ordem, serviços ativos, cidades
 * ativas por UF e nome sem acento. Sem token, sem ids internos e sem dado que o front não pode usar.
 * Cada teste roda numa transação desfeita no fim (o MockMvc roda na mesma thread).
 */
@Transactional
class CatalogoPublicoIntegracaoTest extends IntegracaoTest {

    private static final String URL = "/api/publico/catalogo";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogoPublico catalogo;

    @Autowired
    JsonMapper json;

    @BeforeEach
    @AfterEach
    void cacheLimpo() {
        catalogo.invalidarCache();
    }

    private Map<String, Object> resposta() throws Exception {
        String corpo = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readValue(corpo, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    @DisplayName("200 sem token, com no-store e a CSP de toda resposta")
    void publicoSemToken() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("só GET: POST no mesmo caminho exige login (não está entre as rotas públicas)")
    void soGet() throws Exception {
        mockMvc.perform(post(URL).header("Origin", ORIGEM_DO_FRONT)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("áreas e profissões ativas na ordem do catálogo; encanador e montador (inativas) não aparecem")
    void profissoesAtivasNaOrdem() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.areas[*].codigo").value(Matchers.contains("obra", "casa")))
                .andExpect(jsonPath("$.areas[0].nome").value("Obra e reforma"))
                .andExpect(jsonPath("$.areas[0].profissoes[*].codigo")
                        .value(Matchers.contains("pedreiro", "pintor", "eletricista")))
                .andExpect(
                        jsonPath("$.areas[1].profissoes[*].codigo").value(Matchers.contains("diarista", "jardineiro")))
                .andExpect(jsonPath("$.areas[0].profissoes[0].nome").value("Pedreiro"))
                .andExpect(jsonPath("$.areas[0].profissoes[0].nomePlural").value("Pedreiros"))
                .andExpect(jsonPath("$.areas[0].profissoes[0].icone").value("brick"));
    }

    @Test
    @DisplayName("área sem nenhuma profissão ativa não aparece")
    void areaVaziaNaoAparece() throws Exception {
        jdbc.update("INSERT INTO area (codigo, nome, ordem) VALUES ('teste_vazia', 'Área vazia', 0)");
        jdbc.update(
                "INSERT INTO profissao (area_id, codigo, nome, nome_plural, icone, ativa, ordem) "
                        + "SELECT id, 'teste_inativa', 'Inativa', 'Inativas', 'x', false, 1 FROM area WHERE codigo = 'teste_vazia'");

        mockMvc.perform(get(URL)).andExpect(jsonPath("$.areas[*].codigo").value(Matchers.contains("obra", "casa")));
    }

    @Test
    @DisplayName("serviços ativos da profissão, na ordem; serviço inativo não aparece")
    void servicosAtivosNaOrdem() throws Exception {
        jdbc.update("UPDATE servico SET ativo = false WHERE nome = 'Reboco' "
                + "AND profissao_id = (SELECT id FROM profissao WHERE codigo = 'pedreiro')");

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.areas[0].profissoes[0].servicos[0].nome").value("Alvenaria"))
                .andExpect(jsonPath("$.areas[0].profissoes[0].servicos[1].nome").value("Contrapiso"))
                .andExpect(jsonPath("$.areas[0].profissoes[0].servicos[*].nome")
                        .value(Matchers.not(Matchers.hasItem("Reboco"))));
    }

    @Test
    @DisplayName("cidades ativas por UF e nome sem acento (Ilhota antes de Indaial e Itajaí); inativa não aparece")
    void cidadesAtivasEmOrdemSemAcento() throws Exception {
        jdbc.update("INSERT INTO cidade (codigo_ibge, nome, uf, latitude, longitude, ativa) VALUES "
                + "(4207007, 'Ilhota', 'SC', -26.9, -48.8, true), "
                + "(4100103, 'Abatiá', 'PR', -23.3, -50.3, true), "
                + "(4204608, 'Criciúma', 'SC', -28.7, -49.4, false)");

        Map<String, Object> corpo = resposta();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cidades = (List<Map<String, Object>>) corpo.get("cidades");
        assertThat(cidades)
                .extracting(cidade -> cidade.get("nome"))
                .containsExactly(
                        "Abatiá",
                        "Balneário Camboriú",
                        "Blumenau",
                        "Brusque",
                        "Gaspar",
                        "Ilhota",
                        "Indaial",
                        "Itajaí",
                        "Jaraguá do Sul",
                        "Navegantes",
                        "Pomerode",
                        "Timbó");
        assertThat(cidades.get(0))
                .containsExactlyInAnyOrderEntriesOf(Map.of("codigoIbge", 4100103, "nome", "Abatiá", "uf", "PR"));
    }

    @Test
    @DisplayName("nunca expõe faixa de diária, NR-10, LC 150 nem ids internos")
    void semCamposProibidos() throws Exception {
        String corpo = mockMvc.perform(get(URL)).andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .doesNotContainIgnoringCase("faixa")
                .doesNotContainIgnoringCase("nr10")
                .doesNotContainIgnoringCase("lc150")
                .doesNotContain("\"id\"")
                .doesNotContain("ativa")
                .doesNotContain("ordem")
                .doesNotContain("latitude");
    }

    @Test
    @DisplayName(
            "formato exato: áreas {codigo, nome, profissoes}; profissão {codigo, nome, nomePlural, icone, servicos}")
    void formatoExato() throws Exception {
        Map<String, Object> corpo = resposta();

        assertThat(corpo).containsOnlyKeys("areas", "cidades");
        @SuppressWarnings("unchecked")
        Map<String, Object> area = ((List<Map<String, Object>>) corpo.get("areas")).get(0);
        assertThat(area).containsOnlyKeys("codigo", "nome", "profissoes");
        @SuppressWarnings("unchecked")
        Map<String, Object> profissao = ((List<Map<String, Object>>) area.get("profissoes")).get(0);
        assertThat(profissao).containsOnlyKeys("codigo", "nome", "nomePlural", "icone", "servicos");
        @SuppressWarnings("unchecked")
        Map<String, Object> servico = ((List<Map<String, Object>>) profissao.get("servicos")).get(0);
        assertThat(servico).containsOnlyKeys("nome");
    }

    @Test
    @DisplayName("guarda por 60 s: profissão ativada agora só aparece depois do prazo do cache")
    void cacheDeSessentaSegundos() throws Exception {
        resposta();
        jdbc.update("UPDATE profissao SET ativa = true WHERE codigo = 'encanador'");

        mockMvc.perform(get(URL))
                .andExpect(
                        jsonPath("$.areas[0].profissoes[*].codigo").value(Matchers.not(Matchers.hasItem("encanador"))));

        relogio.avancar(java.time.Duration.ofSeconds(60));

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.areas[0].profissoes[*].codigo")
                        .value(Matchers.contains("pedreiro", "pintor", "eletricista", "encanador")));
    }
}
