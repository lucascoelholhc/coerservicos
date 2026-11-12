package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * Perfil depois de aprovado (RN15a, 11/11): valor, dias, cidades, serviços e "Sobre" mudam na hora, com a censura e
 * auditados; a chave Pix continua só pelo suporte (a conferência do admin vale para ela); foto nova fica oculta e
 * documento novo fica pendente até o admin conferir, com o perfil seguindo na busca com o que já foi aprovado.
 */
class PerfilEditavelTest extends IntegracaoTest {

    private static final String BASE = "/api/profissional/cadastro";

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private ProfissionaisDeTeste.Criado criado;
    private UUID admin;

    @BeforeEach
    void preparar() {
        criado = ProfissionaisDeTeste.ativo(contexto);
        admin = new ContasDeTeste(jdbc, codificador)
                .criarConfirmada(Papel.ADMIN)
                .id();
    }

    private String doProfissional() {
        return bearer(criado.usuario(), Papel.PROFISSIONAL);
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    private ResultActions etapa(String rota, String corpo) throws Exception {
        return mockMvc.perform(put(BASE + "/" + rota)
                .header("Authorization", doProfissional())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions enviarFoto() throws Exception {
        byte[] jpeg = CadastroDeTeste.jpeg();
        return mockMvc.perform(multipart(BASE + "/fotos")
                .file(new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", jpeg))
                .param("servico", "Corte de grama")
                .param("legenda", "Jardim novo")
                .with(CadastroDeTeste.comTamanho(jpeg.length + 512))
                .header("Authorization", doProfissional()));
    }

    private ResultActions enviarDocumento() throws Exception {
        byte[] jpeg = CadastroDeTeste.jpeg();
        return mockMvc.perform(multipart(BASE + "/documentos")
                .file(new MockMultipartFile("arquivo", "doc.jpg", "image/jpeg", jpeg))
                .param("tipo", "cnh")
                .with(CadastroDeTeste.comTamanho(jpeg.length + 512))
                .header("Authorization", doProfissional()));
    }

    private int fotosNoPerfilPublico() throws Exception {
        String json = mockMvc.perform(get("/api/publico/profissionais/" + criado.profissional())
                        .with(r -> {
                            r.setRemoteAddr("10.77.0." + (1 + (int) (Math.random() * 200)));
                            return r;
                        }))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<java.util.List<Object>>read(json, "$.fotos").size();
    }

    @Test
    @DisplayName("aprovado: valor muda na hora, auditado, e o perfil continua ativo")
    void valorMudaNaHora() throws Exception {
        etapa("valor", "{\"valorDiaria\":\"230.00\",\"horario\":\"7h às 16h\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT valor_diaria FROM profissional WHERE id = ?", BigDecimal.class, criado.profissional()))
                .isEqualByComparingTo("230.00");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM profissional WHERE id = ?", String.class, criado.profissional()))
                .isEqualTo("ativo");
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM log_auditoria
                         WHERE acao = 'profissional.perfil-alterado' AND entidade_id = ? AND depois->>'etapa' = 'valor'
                        """, Integer.class, criado.profissional()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("aprovado: o Sobre passa pela censura (422 texto-com-contato); a chave Pix não muda por aqui")
    void sobreComCensuraEPixBloqueado() throws Exception {
        etapa("sobre", "{\"frases\":[\"Cuido do seu jardim.\"],\"texto\":\"Me chama no 47 99988 7766.\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        etapa("pix", "{\"tipo\":\"aleatoria\",\"chave\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cadastro-em-analise"));
    }

    @Test
    @DisplayName("aprovado: foto nova fica oculta até o admin aprovar; aprovada, aparece no perfil público")
    void fotoNovaEsperaOAdmin() throws Exception {
        int antes = fotosNoPerfilPublico();
        String foto = JsonPath.read(
                enviarFoto()
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");

        assertThat(fotosNoPerfilPublico()).isEqualTo(antes);
        mockMvc.perform(get("/api/admin/profissionais/pendencias").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.profissionalId == '" + criado.profissional() + "')].fotos[0].id")
                        .value(foto));

        mockMvc.perform(post("/api/admin/profissionais/" + criado.profissional() + "/fotos/" + foto + "/aprovar")
                        .header("Authorization", doAdmin()))
                .andExpect(status().isNoContent());

        assertThat(fotosNoPerfilPublico()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("aprovado: documento novo manda o cadastro inteiro para a fila de verificação e o tira da busca")
    void documentoNovoEsperaOAdmin() throws Exception {
        String documento = JsonPath.read(
                enviarDocumento()
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM documento_verificacao WHERE id = ?",
                        String.class,
                        UUID.fromString(documento)))
                .isEqualTo("pendente");

        // o cadastro inteiro volta para a fila de verificação do admin e sai da busca até a nova aprovação
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM profissional WHERE id = ?", String.class, criado.profissional()))
                .isEqualTo("em_analise");
        mockMvc.perform(get("/api/publico/profissionais/" + criado.profissional())
                        .with(r -> {
                            r.setRemoteAddr("10.78.0.9");
                            return r;
                        }))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/verificacao").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '" + criado.profissional() + "')]")
                        .isNotEmpty());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'profissional.voltou-para-analise' AND entidade_id = ?",
                        Integer.class,
                        criado.profissional()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("o admin não aprova foto do próprio perfil (403 verificacao-propria)")
    void adminNaoDecideOProprio() throws Exception {
        jdbc.update(
                "INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'ADMIN') ON CONFLICT DO NOTHING",
                criado.usuario());
        String foto = JsonPath.read(
                enviarFoto()
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");

        mockMvc.perform(post("/api/admin/profissionais/" + criado.profissional() + "/fotos/" + foto + "/aprovar")
                        .header("Authorization", bearer(criado.usuario(), Papel.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:verificacao-propria"));
    }

    @Test
    @DisplayName("pendências só para ADMIN; aprovar foto de outro profissional = 404")
    void soAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/profissionais/pendencias").header("Authorization", doProfissional()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/profissionais/" + UUID.randomUUID() + "/fotos/" + UUID.randomUUID()
                                + "/aprovar")
                        .header("Authorization", doAdmin()))
                .andExpect(status().isNotFound());
    }
}
