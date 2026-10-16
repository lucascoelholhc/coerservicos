package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** Documento, selfie, NR-10 e fotos do cadastro: multipart, só do dono, URL assinada curta, auditoria. */
class ArquivosDoCadastroTest extends IntegracaoTest {

    private static final String BASE = CadastroDeTeste.BASE;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private CadastroDeTeste cadastroDe(UUID usuario) {
        return new CadastroDeTeste(mockMvc, bearer(usuario, Papel.CLIENTE));
    }

    /** O mesmo corpo do inexistente, tirando o id único da requisição e o caminho (que tem o id pedido). */
    private static String semInstancia(String corpo) {
        return semRequisicao(corpo).replaceAll(",?\"instance\":\"[^\"]*\"", "");
    }

    private static UUID idDe(String corpo) {
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    @Test
    @DisplayName("documento: 201 com id e tipo (nunca a chave do arquivo); auditado")
    void documento() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();

        String corpo = cadastroDe(usuario)
                .documento("rg_frente")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("rg_frente"))
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(content().string(not(containsString("documento/"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = idDe(corpo);

        String chave =
                jdbc.queryForObject("SELECT chave_arquivo FROM documento_verificacao WHERE id = ?", String.class, id);
        assertThat(chave).startsWith("documento/").doesNotContain("foto.jpg");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE ator_id = ? AND acao = 'profissional.documento-enviado'"
                                + " AND entidade_id = ?",
                        Integer.class,
                        usuario,
                        id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("URL do documento: 2 min, baixa a imagem; de outro usuário = 404 igual ao inexistente")
    void urlDoDocumento() throws Exception {
        UUID dono = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID id = idDe(
                cadastroDe(dono).documento("selfie").andReturn().getResponse().getContentAsString());

        String resposta = mockMvc.perform(
                        get(BASE + "/documentos/" + id + "/url").header("Authorization", bearer(dono, Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        URI endereco = URI.create(JsonPath.read(resposta, "$.url"));
        assertThat(endereco.getQuery()).contains("X-Amz-Expires=120");
        assertThat(HTTP.send(HttpRequest.newBuilder(endereco).build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);

        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(outro).etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");
        String deOutro = mockMvc.perform(
                        get(BASE + "/documentos/" + id + "/url").header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String inexistente = mockMvc.perform(get(BASE + "/documentos/" + UUID.randomUUID() + "/url")
                        .header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(semInstancia(deOutro)).isEqualTo(semInstancia(inexistente));
    }

    @Test
    @DisplayName("tipo mentiroso (HTML chamado foto.jpg com image/jpeg): 415")
    void tipoMentiroso() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        cadastroDe(usuario)
                .enviarArquivo("documentos", html, "foto.jpg", "image/jpeg", p -> p.param("tipo", "rg_frente"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:arquivo-tipo-invalido"));
    }

    @Test
    @DisplayName("certificado NR-10 só para eletricista: 422; tipo desconhecido = 400")
    void nr10() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");

        cadastro.documento("certificado_nr10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:documento-tipo-invalido"));
        cadastro.documento("passaporte").andExpect(status().isBadRequest());
        cadastro.etapa("trabalho", """
                {"profissao":"eletricista","outras":[]}""");
        cadastro.documento("certificado_nr10").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("mandar de novo o mesmo tipo substitui o anterior")
    void substitui() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        UUID primeiro =
                idDe(cadastro.documento("selfie").andReturn().getResponse().getContentAsString());
        String chaveVelha = jdbc.queryForObject(
                "SELECT chave_arquivo FROM documento_verificacao WHERE id = ?", String.class, primeiro);

        cadastro.documento("selfie").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM documento_verificacao d JOIN profissional p ON p.id = d.profissional_id"
                                + " WHERE p.usuario_id = ? AND d.tipo = 'selfie'",
                        Integer.class,
                        usuario))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM documento_verificacao WHERE chave_arquivo = ?",
                        Integer.class,
                        chaveVelha))
                .isZero();
    }

    @Test
    @DisplayName("apagar documento: 204; de outro usuário = 404")
    void apagarDocumento() throws Exception {
        UUID dono = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID id = idDe(cadastroDe(dono)
                .documento("rg_frente")
                .andReturn()
                .getResponse()
                .getContentAsString());
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(outro).etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");

        mockMvc.perform(delete(BASE + "/documentos/" + id).header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/documentos/" + id).header("Authorization", bearer(dono, Papel.CLIENTE)))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM documento_verificacao WHERE id = ?", Integer.class, id)
                        == 0);
    }

    @Test
    @DisplayName("foto do portfólio: legenda com telefone = 422; serviço fora do cadastro = 422; ok = 201")
    void fotos() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");

        cadastro.foto(null, "Liga 47 99999-0001")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        cadastro.foto("Pintura interna", "Sala")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:servico-invalido"));
        cadastro.foto("Alvenaria", "Muro de 12 m")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servico").value("Alvenaria"))
                .andExpect(jsonPath("$.legenda").value("Muro de 12 m"));
    }

    @Test
    @DisplayName("foto de outro usuário: descrever, apagar e URL = 404 igual ao inexistente")
    void fotoDeOutro() throws Exception {
        UUID dono = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID foto = idDe(
                cadastroDe(dono).foto(null, "Muro").andReturn().getResponse().getContentAsString());
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(outro).etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");
        String token = bearer(outro, Papel.CLIENTE);

        String url = mockMvc.perform(get(BASE + "/fotos/" + foto + "/url").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String inexistente = mockMvc.perform(
                        get(BASE + "/fotos/" + UUID.randomUUID() + "/url").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(semInstancia(url)).isEqualTo(semInstancia(inexistente));
        mockMvc.perform(delete(BASE + "/fotos/" + foto).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE + "/fotos/" + foto)
                        .header("Authorization", token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"legenda":"minha agora"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/fotos/" + foto + "/url").header("Authorization", bearer(dono, Papel.CLIENTE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no máximo 12 fotos: a 13ª dá 422 limite-de-fotos; a ordem não repete depois de apagar")
    void limiteDeFotos() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        UUID primeira = null;
        for (int i = 0; i < 12; i++) {
            String corpo = cadastro.foto(null, "Foto " + i)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (primeira == null) {
                primeira = idDe(corpo);
            }
        }

        cadastro.foto(null, "Foto 13")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:limite-de-fotos"));
        mockMvc.perform(delete(BASE + "/fotos/" + primeira).header("Authorization", bearer(usuario, Papel.CLIENTE)))
                .andExpect(status().isNoContent());
        cadastro.foto(null, "Foto nova").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT f.ordem) = count(*) FROM foto_portfolio f JOIN profissional p"
                                + " ON p.id = f.profissional_id WHERE p.usuario_id = ?",
                        Boolean.class,
                        usuario))
                .isTrue();
    }

    @Test
    @DisplayName("em análise, mandar documento ou foto dá 422 cadastro-em-analise")
    void uploadEmAnalise() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.completo();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE + "/envio")
                        .header("Authorization", bearer(usuario, Papel.CLIENTE))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"versaoTermosAceita":"1.0","aceitoRegraDeContato":true}"""))
                .andExpect(status().isOk());

        cadastro.documento("rg_verso")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cadastro-em-analise"));
        cadastro.foto(null, "Mais uma").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("ver e apagar documento ficam na auditoria (acesso a dado sensível)")
    void auditoriaDeDocumento() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID id = idDe(
                cadastroDe(usuario).documento("cnh").andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(BASE + "/documentos/" + id + "/url")
                        .header("Authorization", bearer(usuario, Papel.CLIENTE)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/documentos/" + id).header("Authorization", bearer(usuario, Papel.CLIENTE)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList(
                        "SELECT acao FROM log_auditoria WHERE ator_id = ? AND entidade_id = ?",
                        String.class,
                        usuario,
                        id))
                .contains(
                        "profissional.documento-enviado",
                        "profissional.documento-visto",
                        "profissional.documento-removido");
    }

    @Test
    @DisplayName("sem login: 401 no upload")
    void semLogin() throws Exception {
        mockMvc.perform(multipart(BASE + "/documentos")
                        .file(new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", CadastroDeTeste.jpeg()))
                        .param("tipo", "rg_frente")
                        .with(CadastroDeTeste.comTamanho(2048)))
                .andExpect(status().isUnauthorized());
    }
}
