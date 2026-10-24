package br.com.coe.servicos.mensagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DOM-05: conversa entre cliente e profissional com censura de contato antes do pagamento (RN45–RN50). */
class ChatTest extends IntegracaoTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado profissional;
    private UUID cliente;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
    }

    private String doCliente() {
        return bearer(cliente, Papel.CLIENTE);
    }

    private String doProfissional() {
        return bearer(profissional.usuario(), Papel.CLIENTE, Papel.PROFISSIONAL);
    }

    private ResultActions abrir(String autorizacao, UUID perfil) throws Exception {
        return mockMvc.perform(post("/api/conversas")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profissionalId\":\"" + perfil + "\"}"));
    }

    private String conversa() throws Exception {
        return JsonPath.read(
                abrir(doCliente(), profissional.profissional())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");
    }

    private ResultActions mandarTexto(String autorizacao, String conversa, String texto) throws Exception {
        return mockMvc.perform(post("/api/conversas/" + conversa + "/mensagens")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"" + texto.replace("\"", "\\\"") + "\"}"));
    }

    private ResultActions mensagens(String autorizacao, String conversa) throws Exception {
        return mockMvc.perform(get("/api/conversas/" + conversa + "/mensagens").header("Authorization", autorizacao));
    }

    @Test
    @DisplayName("o cliente abre a conversa com um profissional ativo; abrir de novo devolve a mesma")
    void abrirConversa() throws Exception {
        String primeira = JsonPath.read(
                abrir(doCliente(), profissional.profissional())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.outroNome").value(endsWith(".")))
                        .andExpect(jsonPath("$.contatoLiberado").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");

        abrir(doCliente(), profissional.profissional())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(primeira));
    }

    @Test
    @DisplayName("perfil inexistente: 404; o próprio perfil: 422 conversa-propria")
    void abrirInvalida() throws Exception {
        abrir(doCliente(), UUID.randomUUID()).andExpect(status().isNotFound());
        abrir(doProfissional(), profissional.profissional())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conversa-propria"));
    }

    @Test
    @DisplayName("mensagem sem contato chega ao outro lado; CEP passa (PA13); marcar como lida")
    void mensagemEntregue() throws Exception {
        String id = conversa();

        mandarTexto(doCliente(), id, "Oi, preciso cortar a grama no CEP 89010-000, dá para sábado?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem.bloqueada").value(false))
                .andExpect(jsonPath("$.aviso").doesNotExist());

        mensagens(doProfissional(), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].minha").value(false))
                .andExpect(jsonPath("$.itens[0].texto").value(containsString("89010-000")));
        mockMvc.perform(get("/api/conversas").header("Authorization", doProfissional()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id=='" + id + "')].naoLidas").value(1));

        mockMvc.perform(post("/api/conversas/" + id + "/lidas").header("Authorization", doProfissional()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/conversas").header("Authorization", doProfissional()))
                .andExpect(jsonPath("$.itens[?(@.id=='" + id + "')].naoLidas").value(0));
    }

    @Test
    @DisplayName("mensagem com telefone: não chega; o autor vê bloqueada, mascarada e com o motivo; vira tentativa")
    void mensagemBloqueada() throws Exception {
        String id = conversa();
        int limite = configuracao.tentativasAntesAnalise();

        mandarTexto(doCliente(), id, "me chama no 47 99999-0001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem.bloqueada").value(true))
                .andExpect(jsonPath("$.mensagem.motivos[0]").value("telefone"))
                .andExpect(jsonPath("$.mensagem.texto").value("me chama no •• •••••-••••"))
                .andExpect(jsonPath("$.aviso.tentativa").value(1))
                .andExpect(jsonPath("$.aviso.limite").value(limite))
                .andExpect(jsonPath("$.aviso.contaEmAnalise").value(false));

        mensagens(doProfissional(), id).andExpect(jsonPath("$.itens", hasSize(0)));
        mensagens(doCliente(), id).andExpect(jsonPath("$.itens[0].bloqueada").value(true));
        String trecho = jdbc.queryForObject(
                "SELECT trecho_mascarado FROM tentativa_contato WHERE usuario_id = ? AND origem = 'chat'",
                String.class,
                cliente);
        assertThat(trecho).doesNotContain("99999").doesNotContain("0001");
    }

    @Test
    @DisplayName("passou do limite de tentativas: conta em análise, auditada, e sem chat até o admin decidir (RN49)")
    void contaEmAnalise() throws Exception {
        String id = conversa();
        int limite = configuracao.tentativasAntesAnalise();
        for (int i = 1; i <= limite; i++) {
            mandarTexto(doCliente(), id, "meu zap é 47 99999-000" + i)
                    .andExpect(jsonPath("$.aviso.contaEmAnalise").value(false));
        }

        mandarTexto(doCliente(), id, "manda no insta @joao_cliente")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aviso.tentativa").value(limite + 1))
                .andExpect(jsonPath("$.aviso.contaEmAnalise").value(true));

        assertThat(jdbc.queryForObject("SELECT status FROM usuario WHERE id = ?", String.class, cliente))
                .isEqualTo("em_analise");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE entidade_id = ? AND acao = 'conta.em-analise'",
                        Integer.class,
                        cliente))
                .isEqualTo(1);
        mandarTexto(doCliente(), id, "tudo bem?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-em-analise"));
        mensagens(doCliente(), id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("mensagens antigas pelo cursor da mensagem (antes=<id>), sem perder as do mesmo instante")
    void paginacao() throws Exception {
        String id = conversa();
        for (int i = 1; i <= 3; i++) {
            mandarTexto(doCliente(), id, "mensagem " + i).andExpect(status().isCreated());
        }
        String todas = mensagens(doCliente(), id).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String ultima = JsonPath.read(todas, "$.itens[2].id");

        mockMvc.perform(get("/api/conversas/" + id + "/mensagens")
                        .param("antes", ultima)
                        .header("Authorization", doCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].id").value((String) JsonPath.read(todas, "$.itens[0].id")))
                .andExpect(jsonPath("$.itens[1].id").value((String) JsonPath.read(todas, "$.itens[1].id")));
    }

    @Test
    @DisplayName("número ditado por extenso também não fica guardado: a mensagem vira texto genérico")
    void ditadoNaoFicaGuardado() throws Exception {
        String id = conversa();

        mandarTexto(doCliente(), id, "anota aí quatro sete nove nove nove nove zero zero zero um")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem.bloqueada").value(true))
                .andExpect(jsonPath("$.mensagem.texto").value("[mensagem com contato]"));
    }

    @Test
    @DisplayName("o profissional responde; marcar lidas de conversa alheia é 404; sem token, 401")
    void respostaELidas() throws Exception {
        String id = conversa();
        mandarTexto(doProfissional(), id, "Oi! Sábado eu posso, sim.").andExpect(status().isCreated());

        mensagens(doCliente(), id)
                .andExpect(jsonPath("$.itens[0].minha").value(false))
                .andExpect(jsonPath("$.itens[0].texto").value("Oi! Sábado eu posso, sim."));
        String estranho = bearer(contas.criarConfirmada(Papel.CLIENTE).id(), Papel.CLIENTE);
        mockMvc.perform(post("/api/conversas/" + id + "/lidas").header("Authorization", estranho))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversas/" + id + "/lidas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/conversas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profissionalId\":\"" + profissional.profissional() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("contato liberado (contrato pago): o telefone passa sem censura (RN50)")
    void contatoLiberado() throws Exception {
        String id = conversa();
        jdbc.update("UPDATE conversa SET contato_liberado_em = now() WHERE id = ?::uuid", id);

        mandarTexto(doCliente(), id, "meu celular é 47 99999-0001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem.bloqueada").value(false));
    }

    @Test
    @DisplayName("conversa de outros: 404 igual a inexistente; sem token 401; texto vazio ou longo 400")
    void antiIdor() throws Exception {
        String id = conversa();
        String estranho = bearer(contas.criarConfirmada(Papel.CLIENTE).id(), Papel.CLIENTE);

        String deOutro = mensagens(estranho, id)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String inexistente = mensagens(estranho, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        // iguais tirando o "instance" (o caminho com o id): nada diz que a conversa de outro existe
        assertThat(semRequisicao(deOutro).replaceAll("\"instance\":\"[^\"]*\",?", ""))
                .isEqualTo(semRequisicao(inexistente).replaceAll("\"instance\":\"[^\"]*\",?", ""));
        mandarTexto(estranho, id, "oi").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversas/" + id + "/mensagens")).andExpect(status().isUnauthorized());
        mandarTexto(doCliente(), id, "   ").andExpect(status().isBadRequest());
        mandarTexto(doCliente(), id, "a".repeat(2001)).andExpect(status().isBadRequest());
    }
}
