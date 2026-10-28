package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;

/**
 * Contas no painel do admin (DOM-11; RF52): achar pelo nome, celular ou e-mail (a lista sai mascarada), suspender com
 * motivo (sessões revogadas, auditado) e reativar. Conta da equipe e a própria conta não são suspensas por aqui.
 */
class AdminUsuariosTest extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private ContasDeTeste contas;
    private UUID admin;

    @BeforeEach
    void criarContas() {
        contas = new ContasDeTeste(jdbc, codificador);
        admin = contas.criarConfirmada(Papel.ADMIN).id();
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    private ResultActions suspender(UUID id, String motivo) throws Exception {
        return mockMvc.perform(post("/api/admin/usuarios/" + id + "/suspender")
                .header("Authorization", doAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private ResultActions reativar(UUID id) throws Exception {
        return mockMvc.perform(post("/api/admin/usuarios/" + id + "/reativar").header("Authorization", doAdmin()));
    }

    private ResultActions entrar(ContasDeTeste.Conta conta) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"senha\":\"%s\"}".formatted(conta.email(), ContasDeTeste.SENHA)));
    }

    @Test
    @DisplayName("busca pelo e-mail ou celular exato e pelo nome; contato sempre mascarado na resposta")
    void busca() throws Exception {
        ContasDeTeste.Conta cliente = contas.criarConfirmada(Papel.CLIENTE);
        for (String busca : new String[] {cliente.email(), cliente.celular(), cliente.nome()}) {
            String corpo = mockMvc.perform(
                            get("/api/admin/usuarios").param("busca", busca).header("Authorization", doAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[0].id").value(cliente.id().toString()))
                    .andExpect(jsonPath("$.itens[0].papeis[0]").value("CLIENTE"))
                    .andExpect(jsonPath("$.itens[0].status").value("ativo"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(corpo).doesNotContain(cliente.email()).doesNotContain(cliente.celular());
        }
    }

    @Test
    @DisplayName("suspender: conta suspensa com motivo, todas as sessões revogadas, login recusado e auditoria")
    void suspenderEReativar() throws Exception {
        ContasDeTeste.Conta cliente = contas.criarConfirmada(Papel.CLIENTE);
        entrar(cliente).andExpect(status().isOk());

        suspender(cliente.id(), "Golpe confirmado em conversa.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("suspenso"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revogado_em IS NULL",
                        Long.class,
                        cliente.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT motivo_status FROM usuario WHERE id = ?", String.class, cliente.id()))
                .isEqualTo("Golpe confirmado em conversa.");
        entrar(cliente)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-suspensa"));
        suspender(cliente.id(), "De novo, por engano.").andExpect(status().isConflict());

        reativar(cliente.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativo"));
        entrar(cliente).andExpect(status().isOk());
        assertThat(jdbc.queryForList(
                        "SELECT acao FROM log_auditoria WHERE entidade_id = ? AND ator_id = ? ORDER BY id",
                        String.class,
                        cliente.id(),
                        admin))
                .containsExactly("conta.suspender", "conta.reativar");
    }

    @Test
    @DisplayName("conta em análise pelo chat (RN49) é reativada pelo admin")
    void reativarContaEmAnalise() throws Exception {
        ContasDeTeste.Conta cliente = contas.criar("em_analise", false, Papel.CLIENTE);
        reativar(cliente.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativo"));
    }

    @Test
    @DisplayName("não suspende conta da equipe nem a própria (403); conta inexistente 404; só ADMIN")
    void limites() throws Exception {
        UUID outroAdmin = contas.criarConfirmada(Papel.ADMIN).id();
        suspender(outroAdmin, "Tentativa de suspender admin.")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-da-equipe"));
        suspender(admin, "Tentativa de suspender a si mesmo.").andExpect(status().isForbidden());
        suspender(UUID.randomUUID(), "Conta que não existe.").andExpect(status().isNotFound());
        UUID cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(cliente, Papel.CLIENTE)))
                .andExpect(status().isForbidden());
    }
}
