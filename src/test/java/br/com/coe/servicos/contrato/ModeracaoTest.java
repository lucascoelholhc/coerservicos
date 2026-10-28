package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.usuario.Papel;

/**
 * Moderação (DOM-11; RN59): denúncia de quem está na conversa contra o outro lado, fila e decisão do admin; ocultar e
 * reexibir uma avaliação refaz a média do profissional. Tudo do admin auditado.
 */
class ModeracaoTest extends ExecucaoDeDiariaTeste {

    private UUID admin;
    private UUID conversa;

    @BeforeEach
    void prepararConversa() throws Exception {
        admin = contas.criarConfirmada(Papel.ADMIN).id();
        String corpo = mockMvc.perform(post("/api/conversas")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profissionalId\":\"%s\"}".formatted(profissional.profissional())))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        conversa = UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    private ResultActions denunciar(String autorizacao, UUID conversaId) throws Exception {
        return mockMvc.perform(post("/api/denuncias")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"conversaId":"%s","motivo":"golpe","descricao":"Pediu Pix fora do app."}""".formatted(conversaId)));
    }

    private ResultActions decidir(UUID denuncia, String resultado) throws Exception {
        return mockMvc.perform(post("/api/admin/denuncias/" + denuncia + "/decisao")
                .header("Authorization", doAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultado\":\"" + resultado + "\"}"));
    }

    @Test
    @DisplayName("cliente denuncia o profissional da conversa; a fila do admin mostra; decidir uma vez, auditado")
    void denuncia() throws Exception {
        String corpo = denunciar(doCliente(), conversa)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("aberta"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        UUID denuncia = UUID.fromString(JsonPath.read(corpo, "$.id"));
        assertThat(jdbc.queryForObject("SELECT alvo_usuario_id FROM denuncia WHERE id = ?", UUID.class, denuncia))
                .isEqualTo(profissional.usuario());

        mockMvc.perform(get("/api/admin/denuncias").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '%s')].motivo".formatted(denuncia))
                        .value("golpe"))
                .andExpect(jsonPath("$.itens[?(@.id == '%s')].alvo.id".formatted(denuncia))
                        .value(profissional.usuario().toString()));

        decidir(denuncia, "procedente")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("procedente"));
        decidir(denuncia, "improcedente")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:denuncia-decidida"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE entidade_id = ? AND acao = 'moderacao.denuncia-decidir'",
                        Long.class,
                        denuncia))
                .isEqualTo(1);
        // procedente não suspende sozinho: suspender é outra ação, manual
        assertThat(jdbc.queryForObject("SELECT status FROM usuario WHERE id = ?", String.class, profissional.usuario()))
                .isEqualTo("ativo");
    }

    @Test
    @DisplayName("o profissional também denuncia o cliente; quem não está na conversa recebe 404")
    void denunciaDosDoisLados() throws Exception {
        denunciar(doProfissional(), conversa).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                        "SELECT alvo_usuario_id FROM denuncia WHERE denunciante_id = ? ORDER BY criado_em DESC LIMIT 1",
                        UUID.class,
                        profissional.usuario()))
                .isEqualTo(cliente);
        UUID estranho = contas.criarConfirmada(Papel.CLIENTE).id();
        denunciar(bearer(estranho, Papel.CLIENTE), conversa).andExpect(status().isNotFound());
        denunciar(doCliente(), UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/denuncias").header("Authorization", doCliente()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ocultar uma avaliação tira da média e do perfil; mostrar de novo volta (auditado)")
    void ocultarAvaliacao() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
        String corpo = mockMvc.perform(post("/api/avaliacoes")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contratoId\":\"%s\",\"nota\":2,\"comentario\":\"Não gostei.\"}"
                                .formatted(contrato)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        UUID avaliacao = UUID.fromString(JsonPath.read(corpo, "$.id"));

        mockMvc.perform(post("/api/admin/avaliacoes/" + avaliacao + "/ocultar").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visivel").value(false));
        assertThat(jdbc.queryForObject(
                        "SELECT total_avaliacoes FROM profissional WHERE id = ?",
                        Integer.class,
                        profissional.profissional()))
                .isZero();
        mockMvc.perform(get("/api/publico/profissionais/" + profissional.profissional()))
                .andExpect(jsonPath("$.avaliacoes.novoNaCoe").value(true));

        mockMvc.perform(post("/api/admin/avaliacoes/" + avaliacao + "/mostrar").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visivel").value(true));
        assertThat(jdbc.queryForObject(
                        "SELECT nota_media FROM profissional WHERE id = ?",
                        BigDecimal.class,
                        profissional.profissional()))
                .isEqualByComparingTo("2.00");
        assertThat(jdbc.queryForList(
                        "SELECT acao FROM log_auditoria WHERE entidade_id = ? ORDER BY id", String.class, avaliacao))
                .containsExactly("avaliacao.ocultar", "avaliacao.mostrar");
        mockMvc.perform(get("/api/admin/avaliacoes")
                        .param("profissional", profissional.profissional().toString())
                        .header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '%s')].comentario".formatted(avaliacao))
                        .value("Não gostei."));
    }
}
