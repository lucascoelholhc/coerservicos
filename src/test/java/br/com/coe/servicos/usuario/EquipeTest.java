package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;

/**
 * Equipe de administradores (DOM-11, lacuna de 26/10): convidar uma conta existente, a pessoa aceita e tirar o papel,
 * sempre com código SMS na hora; nunca fica sem ADMIN; quem perde ou ganha o papel tem as sessões revogadas e é
 * avisado; auditoria sem dado pessoal.
 */
class EquipeTest extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private EquipeService equipe;

    @Autowired
    private PlatformTransactionManager transacoes;

    private ContasDeTeste contas;
    private ContasDeTeste.Conta admin;

    @BeforeEach
    void criarAdmin() {
        contas = new ContasDeTeste(jdbc, codificador);
        admin = contas.criarConfirmada(Papel.ADMIN);
    }

    private String doAdmin(ContasDeTeste.Conta conta) {
        return bearer(conta.id(), Papel.ADMIN);
    }

    /** Pede o código da ação ao próprio celular e devolve o que chegou por SMS. */
    private String codigoDoAdmin(ContasDeTeste.Conta conta) throws Exception {
        mockMvc.perform(post("/api/admin/equipe/codigo").header("Authorization", doAdmin(conta)))
                .andExpect(status().isAccepted());
        return sms.ultimoCodigo(conta.celular()).orElseThrow();
    }

    private ResultActions convidar(ContasDeTeste.Conta quem, String login, String codigo) throws Exception {
        return mockMvc.perform(post("/api/admin/equipe/convites")
                .header("Authorization", doAdmin(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"codigo\":\"%s\"}".formatted(login, codigo)));
    }

    private ResultActions remover(ContasDeTeste.Conta quem, UUID alvo, String codigo) throws Exception {
        return mockMvc.perform(post("/api/admin/equipe/" + alvo + "/remover")
                .header("Authorization", doAdmin(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"%s\"}".formatted(codigo)));
    }

    private List<String> papeisDe(UUID usuario) {
        return jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, usuario);
    }

    private void entrar(ContasDeTeste.Conta conta) throws Exception {
        mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"%s\"}".formatted(conta.email(), ContasDeTeste.SENHA)))
                .andExpect(status().isOk());
    }

    private long sessoesAtivas(UUID usuario) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revogado_em IS NULL", Long.class, usuario);
    }

    @Test
    @DisplayName(
            "convidar e aceitar: código na hora dos dois lados; vira ADMIN com MFA, sessões revogadas, avisos e auditoria")
    void convidarEAceitar() throws Exception {
        ContasDeTeste.Conta pessoa = contas.criarConfirmada(Papel.CLIENTE);
        entrar(pessoa);

        convidar(admin, pessoa.email(), "000000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:codigo-invalido"));
        convidar(admin, pessoa.email(), codigoDoAdmin(admin)).andExpect(status().isCreated());
        sms.aguardarEnvios();
        assertThat(sms.para(pessoa.celular())).anyMatch(m -> m.texto().contains("equipe"));

        String daPessoa = bearer(pessoa.id(), Papel.CLIENTE);
        mockMvc.perform(get("/api/contas/eu/convite-admin").header("Authorization", daPessoa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty());
        mockMvc.perform(post("/api/contas/eu/convite-admin/codigo").header("Authorization", daPessoa))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/contas/eu/convite-admin/aceitar")
                        .header("Authorization", daPessoa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"%s\"}"
                                .formatted(sms.ultimoCodigo(pessoa.celular()).orElseThrow())))
                .andExpect(status().isNoContent());

        assertThat(papeisDe(pessoa.id())).contains("ADMIN", "CLIENTE");
        assertThat(jdbc.queryForObject("SELECT mfa_sms_ativo FROM usuario WHERE id = ?", Boolean.class, pessoa.id()))
                .isTrue();
        assertThat(sessoesAtivas(pessoa.id())).isZero();
        mockMvc.perform(get("/api/contas/eu/convite-admin").header("Authorization", daPessoa))
                .andExpect(status().isNotFound());
        List<String> auditoria = jdbc.queryForList(
                "SELECT acao FROM log_auditoria WHERE entidade_id = ? AND acao LIKE 'admin.%' ORDER BY id",
                String.class, pessoa.id());
        assertThat(auditoria).containsExactly("admin.convidar", "admin.aceitar");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE entidade_id = ? AND (antes::text || depois::text) LIKE ?",
                        Long.class,
                        pessoa.id(),
                        "%" + pessoa.celular().substring(4) + "%"))
                .isZero();
    }

    @Test
    @DisplayName("convite: conta que já é ADMIN = 409; login de ninguém = 404; sem papel ADMIN = 403")
    void convitesInvalidos() throws Exception {
        ContasDeTeste.Conta outroAdmin = contas.criarConfirmada(Papel.ADMIN);
        convidar(admin, outroAdmin.email(), codigoDoAdmin(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:ja-e-admin"));
        relogio.avancar(java.time.Duration.ofSeconds(61)); // um código por minuto para o mesmo celular
        convidar(admin, "ninguem-" + UUID.randomUUID() + "@teste.coe.local", codigoDoAdmin(admin))
                .andExpect(status().isNotFound());
        ContasDeTeste.Conta cliente = contas.criarConfirmada(Papel.CLIENTE);
        mockMvc.perform(post("/api/admin/equipe/codigo").header("Authorization", bearer(cliente.id(), Papel.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "remover: tira o papel, revoga as sessões e avisa; quem perdeu o papel não age mais com o token antigo")
    void remover() throws Exception {
        ContasDeTeste.Conta outro = contas.criarConfirmada(Papel.ADMIN);
        remover(admin, outro.id(), "000000").andExpect(status().isUnprocessableEntity());
        assertThat(papeisDe(outro.id())).contains("ADMIN");

        remover(admin, outro.id(), codigoDoAdmin(admin)).andExpect(status().isNoContent());
        assertThat(papeisDe(outro.id())).doesNotContain("ADMIN");
        assertThat(sessoesAtivas(outro.id())).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE entidade_id = ? AND acao = 'admin.remover'",
                        Long.class,
                        outro.id()))
                .isEqualTo(1);

        // o token antigo (até 15 min) ainda diz ADMIN, mas o banco não: 403 em todo o painel
        mockMvc.perform(post("/api/admin/equipe/codigo").header("Authorization", doAdmin(outro)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:fora-da-equipe"));
        mockMvc.perform(get("/api/admin/configuracao").header("Authorization", doAdmin(outro)))
                .andExpect(status().isForbidden());
        assertThat(papeisDe(admin.id())).contains("ADMIN");
    }

    @Test
    @DisplayName("nunca fica sem ADMIN: o último não sai, nem por ele mesmo (409 ultimo-admin)")
    void ultimoAdmin() throws Exception {
        String codigo = codigoDoAdmin(admin);
        TransactionTemplate transacao = new TransactionTemplate(transacoes);
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
                    // só nesta transação (desfeita no fim): todos os outros admins somem
                    jdbc.update("DELETE FROM usuario_papel WHERE papel = 'ADMIN' AND usuario_id <> ?", admin.id());
                    try {
                        equipe.removerComo(admin.id(), admin.id(), codigo);
                    } finally {
                        status.setRollbackOnly();
                    }
                }))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("último");
        assertThat(papeisDe(admin.id())).contains("ADMIN");
    }

    @Test
    @DisplayName("lista da equipe: admins com contato mascarado; só ADMIN")
    void lista() throws Exception {
        String corpo = mockMvc.perform(get("/api/admin/equipe").header("Authorization", doAdmin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admins[?(@.id == '%s')].eu".formatted(admin.id()))
                        .value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(corpo).doesNotContain(admin.celular()).doesNotContain(admin.email());
    }
}
