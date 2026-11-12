package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.usuario.Papel;

/**
 * Regras de produto de 11/11 (docs/regras-negocio.md): o cliente cancela uma diária paga até 24 h antes do dia (RN29a,
 * PA09) com reembolso integral; suspender o profissional reembolsa sozinho as diárias pagas e não iniciadas (RN44f);
 * o profissional não vê a comissão nem o total do cliente (RN34f); reativar a conta zera as tentativas de contato
 * (RN44g).
 */
class RegrasDoDia11Test extends ExecucaoDeDiariaTeste {

    private UUID admin;

    @Override
    protected int quantasDiarias() {
        return 2;
    }

    @BeforeEach
    void preparar() throws Exception {
        admin = contas.criarConfirmada(Papel.ADMIN).id();
        pagar();
    }

    private ResultActions cancelar(String autorizacao, UUID diaria) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + diaria + "/cancelar").header("Authorization", autorizacao));
    }

    /** 00:00 do dia da diária, no fuso de negócio, mais um deslocamento. */
    private void antesDoDia(Duration antes) {
        relogio.fixarEm(dia.atStartOfDay(FusoDeNegocio.ZONA).toInstant().minus(antes));
    }

    @Test
    @DisplayName("cancelar com 24 h ou mais: reembolso integral (diária + comissão) só daquela diária")
    void cancelarNoPrazo() throws Exception {
        antesDoDia(Duration.ofHours(24));

        cancelar(doCliente(), diarias.get(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reembolsada"));

        Map<String, Object> reembolso = jdbc.queryForMap(
                "SELECT motivo, valor_total, status FROM reembolso WHERE diaria_id = ?", diarias.get(0));
        BigDecimal pago = jdbc.queryForObject(
                "SELECT valor + valor_comissao FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        assertThat(reembolso.get("motivo")).isEqualTo("cancelamento");
        assertThat((BigDecimal) reembolso.get("valor_total")).isEqualByComparingTo(pago);
        assertThat(statusDa(diarias.get(1))).isEqualTo("paga");
        sms.aguardarEnvios();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notificacao WHERE usuario_id = ? AND tipo = 'diaria.cancelada'",
                        Integer.class,
                        profissional.usuario()))
                .isPositive();
    }

    @Test
    @DisplayName("cancelar com menos de 24 h: 422 cancelamento-fora-do-prazo, nada muda")
    void cancelarForaDoPrazo() throws Exception {
        antesDoDia(Duration.ofHours(24).minusSeconds(1));

        cancelar(doCliente(), diarias.get(0))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cancelamento-fora-do-prazo"));

        assertThat(statusDa(diarias.get(0))).isEqualTo("paga");
    }

    @Test
    @DisplayName("cancelar: de outro cliente 404, profissional 403, sem token 401")
    void cancelarSoODono() throws Exception {
        antesDoDia(Duration.ofDays(2));
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();

        cancelar(bearer(outro, Papel.CLIENTE), diarias.get(0)).andExpect(status().isNotFound());
        cancelar(doProfissional(), diarias.get(0)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/diarias/" + diarias.get(0) + "/cancelar")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("suspender o profissional reembolsa sozinho as diárias pagas; a que está aguardando segue o fluxo")
    void suspensaoReembolsaAsPagas() throws Exception {
        trabalharODia(diarias.get(0), dia); // aguardando aprovação

        mockMvc.perform(post("/api/admin/profissionais/" + profissional.profissional() + "/suspender")
                        .header("Authorization", bearer(admin, Papel.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Faltas seguidas sem aviso.\"}"))
                .andExpect(status().isOk());

        assertThat(statusDa(diarias.get(0))).isEqualTo("aguardando");
        assertThat(statusDa(diarias.get(1))).isEqualTo("reembolsada");
        assertThat(jdbc.queryForObject(
                        "SELECT motivo FROM reembolso WHERE diaria_id = ?", String.class, diarias.get(1)))
                .isEqualTo("outro");
        sms.aguardarEnvios();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notificacao WHERE usuario_id = ? AND tipo = 'diaria.reembolsada-suspensao'",
                        Integer.class,
                        cliente))
                .isPositive();
    }

    @Test
    @DisplayName("o profissional não vê a comissão nem o total do cliente no contrato (RN34f); o cliente vê")
    void profissionalSemComissao() throws Exception {
        String doProfissional = mockMvc.perform(
                        get("/api/contratos/" + contrato).header("Authorization", doProfissional()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(doProfissional).doesNotContain("valorComissao").doesNotContain("valorTotal");
        assertThat(JsonPath.<String>read(doProfissional, "$.valorServicos")).isNotBlank();

        mockMvc.perform(get("/api/contratos/" + contrato).header("Authorization", doCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorComissao").exists())
                .andExpect(jsonPath("$.valorTotal").exists());
    }

    @Test
    @DisplayName("reativar a conta zera a contagem de tentativas de contato; o histórico fica na auditoria")
    void reativarZeraTentativas() throws Exception {
        String conversa = JsonPath.read(
                mockMvc.perform(post("/api/conversas")
                                .header("Authorization", doCliente())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"profissionalId\":\"" + profissional.profissional() + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");
        jdbc.update("UPDATE conversa SET contato_liberado_em = NULL WHERE id = ?", UUID.fromString(conversa));
        String comContato = "{\"texto\":\"me chama no zap 47 99988 7766\"}";
        mockMvc.perform(post("/api/conversas/" + conversa + "/mensagens")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comContato))
                .andExpect(jsonPath("$.aviso.tentativa").value(1));
        jdbc.update("UPDATE usuario SET status = 'em_analise' WHERE id = ?", cliente);

        mockMvc.perform(post("/api/admin/usuarios/" + cliente + "/reativar")
                        .header("Authorization", bearer(admin, Papel.ADMIN)))
                .andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(1));

        mockMvc.perform(post("/api/conversas/" + conversa + "/mensagens")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comContato))
                .andExpect(jsonPath("$.aviso.tentativa").value(1));
        assertThat(jdbc.queryForObject(
                        "SELECT depois->>'tentativasZeradas' FROM log_auditoria WHERE acao = 'conta.reativar' AND entidade_id = ?",
                        String.class,
                        cliente))
                .isEqualTo("1");
    }
}
