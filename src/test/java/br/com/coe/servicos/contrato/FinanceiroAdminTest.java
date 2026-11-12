package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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
 * Financeiro do admin (DOM-11 parte 2b, RF55): tudo calculado do livro-caixa. Os saldos batem com a view
 * {@code saldo_conta}; a tabela por diária mostra o que cada diária moveu no período (liberado ao profissional,
 * receita, reembolso) e o que ainda está guardado ou em disputa.
 */
class FinanceiroAdminTest extends ExecucaoDeDiariaTeste {

    private UUID admin;

    @Override
    protected int quantasDiarias() {
        return 3;
    }

    @BeforeEach
    void prepararAdmin() throws Exception {
        admin = contas.criarConfirmada(Papel.ADMIN).id();
        pagar();
    }

    private ResultActions financeiro(String autorizacao, String consulta) throws Exception {
        return mockMvc.perform(get("/api/admin/financeiro" + consulta).header("Authorization", autorizacao));
    }

    private ResultActions porDiaria(String consulta) throws Exception {
        return mockMvc.perform(
                get("/api/admin/financeiro/diarias" + consulta).header("Authorization", bearer(admin, Papel.ADMIN)));
    }

    /** O livro-caixa carimba o horário do banco (não o relógio do teste): o período parte da data do banco. */
    private LocalDate hojeNoBanco() {
        return jdbc.queryForObject("SELECT (now() AT TIME ZONE 'America/Sao_Paulo')::date", LocalDate.class);
    }

    private String periodo() {
        LocalDate hoje = hojeNoBanco();
        return "?de=" + hoje.minusDays(30) + "&ate=" + hoje.plusDays(1);
    }

    private void contestar(UUID diaria) throws Exception {
        mockMvc.perform(post("/api/disputas")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diariaId\":\"" + diaria
                                + "\",\"motivo\":\"qualidade\",\"descricao\":\"O serviço ficou pela metade.\"}"))
                .andExpect(status().isCreated());
    }

    private BigDecimal saldoDaView(String tipo) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(saldo), 0) FROM saldo_conta WHERE tipo = ?", BigDecimal.class, tipo);
    }

    private static BigDecimal valor(String json, String caminho) {
        return new BigDecimal(JsonPath.<String>read(json, caminho));
    }

    @Test
    @DisplayName("os saldos do painel batem com a view saldo_conta, conta por conta")
    void saldosBatemComAView() throws Exception {
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());

        String json = financeiro(bearer(admin, Papel.ADMIN), periodo())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(valor(json, "$.saldos.guardado")).isEqualByComparingTo(saldoDaView("custodia"));
        assertThat(valor(json, "$.saldos.aRepassar")).isEqualByComparingTo(saldoDaView("profissional"));
        assertThat(valor(json, "$.saldos.receita")).isEqualByComparingTo(saldoDaView("receita_coe"));
        assertThat(valor(json, "$.saldos.aEstornar")).isEqualByComparingTo(saldoDaView("cliente"));
        // o dinheiro no gateway é o espelho de tudo o que está guardado, a repassar, a estornar e da receita
        assertThat(valor(json, "$.saldos.noGateway"))
                .isEqualByComparingTo(saldoDaView("gateway").negate());
        assertThat(valor(json, "$.saldos.noGateway"))
                .isEqualByComparingTo(saldoDaView("custodia")
                        .add(saldoDaView("profissional"))
                        .add(saldoDaView("receita_coe"))
                        .add(saldoDaView("cliente")));
        assertThat(valor(json, "$.periodo.recebido")).isGreaterThanOrEqualTo(totalDoContrato());
        assertThat(JsonPath.<String>read(json, "$.periodo.de"))
                .isEqualTo(hojeNoBanco().minusDays(30).toString());
    }

    @Test
    @DisplayName("por diária: liberada mostra repasse e receita; contestada fica em disputa; paga fica guardada")
    void tabelaPorDiaria() throws Exception {
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
        trabalharODia(diarias.get(1), dia.plusWeeks(1));
        contestar(diarias.get(1));

        String json = porDiaria(periodo() + "&contrato=" + contrato)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        List<Map<String, Object>> itens = JsonPath.read(json, "$.itens");
        assertThat(itens).hasSize(3);
        Map<String, Object> liberada = itens.get(0);
        BigDecimal valorDia =
                jdbc.queryForObject("SELECT valor FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        BigDecimal comissao =
                jdbc.queryForObject("SELECT valor_comissao FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        assertThat(liberada.get("id")).isEqualTo(diarias.get(0).toString());
        assertThat(liberada.get("status")).isEqualTo("liberada");
        assertThat(new BigDecimal((String) liberada.get("liberadoAoProfissional")))
                .isEqualByComparingTo(valorDia);
        assertThat(new BigDecimal((String) liberada.get("receita"))).isEqualByComparingTo(comissao);
        assertThat(new BigDecimal((String) liberada.get("guardado"))).isEqualByComparingTo("0.00");

        Map<String, Object> contestada = itens.get(1);
        assertThat(contestada.get("status")).isEqualTo("contestada");
        assertThat(new BigDecimal((String) contestada.get("emDisputa"))).isEqualByComparingTo(valorDia.add(comissao));
        assertThat(new BigDecimal((String) contestada.get("liberadoAoProfissional")))
                .isEqualByComparingTo("0.00");

        Map<String, Object> paga = itens.get(2);
        assertThat(paga.get("status")).isEqualTo("paga");
        assertThat(new BigDecimal((String) paga.get("guardado"))).isEqualByComparingTo(valorDia.add(comissao));
        assertThat(json).doesNotContain("celular").doesNotContain("endereco").doesNotContain("@");
    }

    @Test
    @DisplayName("período ao contrário ou acima de um ano = 422 periodo-invalido")
    void periodoInvalido() throws Exception {
        LocalDate hoje = FusoDeNegocio.hoje(relogio);
        financeiro(bearer(admin, Papel.ADMIN), "?de=" + hoje + "&ate=" + hoje.minusDays(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:periodo-invalido"));
        porDiaria("?de=" + hoje.minusDays(400) + "&ate=" + hoje)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:periodo-invalido"));
    }

    @Test
    @DisplayName("só ADMIN: cliente e profissional recebem 403, sem token 401")
    void soAdmin() throws Exception {
        financeiro(doCliente(), periodo()).andExpect(status().isForbidden());
        financeiro(doProfissional(), periodo()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/financeiro")).andExpect(status().isUnauthorized());
        financeiro(bearer(admin, Papel.ADMIN), "").andExpect(status().isOk());
    }
}
