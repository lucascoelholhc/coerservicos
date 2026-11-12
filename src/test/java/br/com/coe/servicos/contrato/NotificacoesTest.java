package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Avisos ao cliente e ao profissional (DOM-11 parte 2b, RN35): pela fila de envio normal, só depois do commit, com
 * texto sem segredo (primeiro nome, número do pedido e data; nunca contato, endereço, código ou link) e registrados na
 * tabela {@code notificacao} com o resultado do envio.
 */
class NotificacoesTest extends ExecucaoDeDiariaTeste {

    private List<Map<String, Object>> avisos(UUID usuario, String tipo) {
        sms.aguardarEnvios();
        email.aguardarEnvios();
        return jdbc.queryForList(
                "SELECT canal, titulo, corpo, status, enviada_em FROM notificacao WHERE usuario_id = ? AND tipo = ?"
                        + " ORDER BY canal",
                usuario,
                tipo);
    }

    private String celularDe(UUID usuario) {
        return jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class, usuario);
    }

    private String emailDe(UUID usuario) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
    }

    private long numeroDoPedido() {
        return jdbc.queryForObject("SELECT numero FROM contrato WHERE id = ?", Long.class, contrato);
    }

    private void semDadoPessoal(String corpo) {
        assertThat(corpo)
                .doesNotContain(celularDe(cliente))
                .doesNotContain(celularDe(profissional.usuario()))
                .doesNotContain(emailDe(cliente))
                .doesNotContain("Rua das Palmeiras")
                .doesNotContain("http");
    }

    @Test
    @DisplayName("pagamento confirmado avisa o cliente e o profissional, com o número do pedido e sem contato")
    void pagamentoConfirmado() throws Exception {
        pagar();

        List<Map<String, Object>> doProfissional = avisos(profissional.usuario(), "pagamento.novo-trabalho");
        Map<String, Object> porSms = doProfissional.stream()
                .filter(a -> "sms".equals(a.get("canal")))
                .findFirst()
                .orElseThrow();
        String corpo = (String) porSms.get("corpo");
        assertThat(corpo).contains("Jardineiro").contains("nº " + numeroDoPedido());
        assertThat(corpo).doesNotContain("Silva"); // só o primeiro nome
        semDadoPessoal(corpo);
        assertThat(porSms.get("status")).isEqualTo("enviada");
        assertThat(porSms.get("enviada_em")).isNotNull();
        assertThat(sms.para(celularDe(profissional.usuario())))
                .extracting(enviado -> enviado.texto())
                .contains(corpo);

        List<Map<String, Object>> doCliente = avisos(cliente, "pagamento.confirmado");
        assertThat(doCliente).isNotEmpty();
        doCliente.forEach(a -> semDadoPessoal((String) a.get("corpo")));
    }

    @Test
    @DisplayName("Cheguei e Terminei avisam o cliente (RN35); a diária liberada avisa o profissional")
    void chegueiTermineiELiberada() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);

        assertThat(avisos(cliente, "diaria.cheguei"))
                .extracting(a -> a.get("canal"))
                .containsExactly("sms");
        List<Map<String, Object>> terminei = avisos(cliente, "diaria.terminei");
        assertThat(terminei).extracting(a -> a.get("canal")).contains("sms");
        terminei.forEach(a -> semDadoPessoal((String) a.get("corpo")));

        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
        List<Map<String, Object>> liberada = avisos(profissional.usuario(), "diaria.liberada");
        assertThat(liberada).isNotEmpty();
        assertThat(liberada).allSatisfy(a -> assertThat(a.get("status")).isEqualTo("enviada"));
    }

    @Test
    @DisplayName("falha do provedor fica registrada como falhou; a ação do usuário não muda")
    void falhaNoEnvio() throws Exception {
        pagar();
        noDia(dia);
        sms.aguardarEnvios();
        sms.falharProximo();
        cheguei(doProfissional(), diarias.get(0)).andExpect(status().isOk());

        List<Map<String, Object>> cheguei = avisos(cliente, "diaria.cheguei");
        assertThat(cheguei).hasSize(1);
        assertThat(cheguei.get(0).get("status")).isEqualTo("falhou");
        assertThat(statusDa(diarias.get(0))).isEqualTo("andamento");
    }

    @Test
    @DisplayName("reclamação aberta avisa o profissional; decidida avisa os dois lados")
    void disputa() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);
        String disputa = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/disputas")
                                .header("Authorization", doCliente())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"diariaId\":\"" + diarias.get(0)
                                        + "\",\"motivo\":\"qualidade\",\"descricao\":\"Ficou pela metade.\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.id");
        assertThat(avisos(profissional.usuario(), "disputa.aberta")).isNotEmpty();

        UUID admin =
                contas.criarConfirmada(br.com.coe.servicos.usuario.Papel.ADMIN).id();
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/admin/disputas/" + disputa + "/decisao")
                                .header("Authorization", bearer(admin, br.com.coe.servicos.usuario.Papel.ADMIN))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"favor\":\"cliente\",\"justificativa\":\"As fotos mostram o serviço pela metade.\"}"))
                .andExpect(status().isOk());
        assertThat(avisos(cliente, "disputa.decidida")).isNotEmpty();
        assertThat(avisos(profissional.usuario(), "disputa.decidida")).isNotEmpty();
    }

    @Test
    @DisplayName("perfil suspenso e reativado pelo admin avisa o profissional")
    void perfilSuspensoEReativado() throws Exception {
        UUID admin =
                contas.criarConfirmada(br.com.coe.servicos.usuario.Papel.ADMIN).id();
        String base = "/api/admin/profissionais/" + profissional.profissional();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base + "/suspender")
                        .header("Authorization", bearer(admin, br.com.coe.servicos.usuario.Papel.ADMIN))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Faltas seguidas.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base + "/reativar")
                        .header("Authorization", bearer(admin, br.com.coe.servicos.usuario.Papel.ADMIN)))
                .andExpect(status().isOk());

        List<Map<String, Object>> suspenso = avisos(profissional.usuario(), "profissional.suspenso");
        assertThat(suspenso).isNotEmpty();
        assertThat((String) suspenso.get(0).get("corpo")).doesNotContain("Faltas seguidas");
        assertThat(avisos(profissional.usuario(), "profissional.reativado")).isNotEmpty();
    }
}
