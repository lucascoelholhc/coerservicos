package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** Área do profissional (FE-06): as diárias dele, o dinheiro pelo livro-caixa e as folgas da agenda. */
class AreaDoProfissionalTest extends ExecucaoDeDiariaTeste {

    @Override
    protected int quantasDiarias() {
        return 2;
    }

    private ResultActions minhasDiarias(String autorizacao) throws Exception {
        return mockMvc.perform(get("/api/profissional/diarias").header("Authorization", autorizacao));
    }

    private ResultActions dinheiro(String autorizacao) throws Exception {
        return mockMvc.perform(get("/api/profissional/dinheiro").header("Authorization", autorizacao));
    }

    private ResultActions folga(String autorizacao, Object data) throws Exception {
        return mockMvc.perform(put("/api/profissional/agenda/folgas/" + data).header("Authorization", autorizacao));
    }

    /** O que o profissional recebe por diária, com a comissão e a taxa congeladas no contrato. */
    private BigDecimal repasse() {
        var c = jdbc.queryForMap("SELECT comissao_pct, taxa_paga_por FROM contrato WHERE id = ?", contrato);
        BigDecimal valor =
                jdbc.queryForObject("SELECT valor FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        return CalculadoraDiaria.calcular(
                        new Dinheiro(valor),
                        new Percentual((BigDecimal) c.get("comissao_pct")),
                        TaxaPagaPor.deCodigo((String) c.get("taxa_paga_por")))
                .repasseProfissional()
                .valor();
    }

    @Test
    void listaAsDiariasDoProfissionalComORepasseENomeCurtoDoCliente() throws Exception {
        pagar();
        String corpo = minhasDiarias(doProfissional())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].id").value(diarias.get(0).toString()))
                .andExpect(jsonPath("$.itens[0].contratoId").value(contrato.toString()))
                .andExpect(jsonPath("$.itens[0].data").value(dia.toString()))
                .andExpect(jsonPath("$.itens[0].status").value("paga"))
                .andExpect(jsonPath("$.itens[0].repasse").value(repasse().toPlainString()))
                .andExpect(jsonPath("$.itens[0].servicos[0]").value("Corte de grama"))
                .andExpect(jsonPath("$.itens[1].data").value(dia.plusWeeks(1).toString()))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String nome = JsonPath.read(corpo, "$.itens[0].cliente");
        assertThat(nome).matches("\\S+( \\S\\.)?");
        assertThat(corpo).doesNotContain("Palmeiras").doesNotContain("88301000").doesNotContain("celular");
    }

    @Test
    void outroProfissionalNaoVeAsDiariasDesteEClienteRecebe403() throws Exception {
        ProfissionaisDeTeste.Criado outro = ProfissionaisDeTeste.ativo(contexto);
        minhasDiarias(bearer(outro.usuario(), Papel.PROFISSIONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
        minhasDiarias(doCliente()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/profissional/diarias")).andExpect(status().isUnauthorized());
        dinheiro(doCliente()).andExpect(status().isForbidden());
    }

    @Test
    void dinheiroVemDoLivroCaixaLiberadoEOQueVaiCair() throws Exception {
        dinheiro(doProfissional())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberado").value("0.00"))
                .andExpect(jsonPath("$.vaiCair").value("0.00"))
                .andExpect(jsonPath("$.emReclamacao").value("0.00"));
        pagar();
        BigDecimal umDia = repasse();
        dinheiro(doProfissional())
                .andExpect(jsonPath("$.vaiCair").value(umDia.add(umDia).toPlainString()))
                .andExpect(jsonPath("$.liberado").value("0.00"));

        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());

        dinheiro(doProfissional())
                .andExpect(jsonPath("$.liberado").value(umDia.toPlainString()))
                .andExpect(jsonPath("$.vaiCair").value(umDia.toPlainString()));
        assertThat(saldoNoContrato("profissional")).isEqualByComparingTo(umDia);
    }

    @Test
    void folgaNumDiaLivreApareceNaAgendaSaiNoDeleteEBloqueiaOPerfil() throws Exception {
        LocalDate livre = dia.plusWeeks(2);
        folga(doProfissional(), livre).andExpect(status().isNoContent());
        folga(doProfissional(), livre).andExpect(status().isNoContent()); // de novo: nada muda
        String agenda = mockMvc.perform(get("/api/profissional/agenda").header("Authorization", doProfissional()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> folgas = JsonPath.read(agenda, "$.folgas");
        assertThat(folgas).containsExactly(livre.toString());
        List<Integer> semana = JsonPath.read(agenda, "$.diasDaSemana");
        assertThat(semana).isNotEmpty();
        assertThat((String) JsonPath.read(agenda, "$.perfilId"))
                .isEqualTo(profissional.profissional().toString());

        String perfil = mockMvc.perform(get("/api/publico/profissionais/" + profissional.profissional()))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> livres = JsonPath.read(perfil, "$.diasLivres");
        assertThat(livres).doesNotContain(livre.toString());

        mockMvc.perform(delete("/api/profissional/agenda/folgas/" + livre).header("Authorization", doProfissional()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM bloqueio_agenda WHERE profissional_id = ? AND data = ?",
                        Integer.class,
                        profissional.profissional(),
                        livre))
                .isZero();
    }

    @Test
    void folgaEmDiaComDiariaOuForaDaJanelaERecusada() throws Exception {
        folga(doProfissional(), dia)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-com-diaria"));
        folga(doProfissional(), dia.minusYears(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-invalido"));
        folga(doProfissional(), dia.plusYears(2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-invalido"));
        folga(doCliente(), dia.plusWeeks(3)).andExpect(status().isForbidden());
        folga(doProfissional(), "nao-e-data").andExpect(status().isBadRequest());
    }

    @Test
    void pedidoNaoPagoNaoApareceEDiariaContestadaVaiParaReclamacao() throws Exception {
        minhasDiarias(doProfissional()).andExpect(jsonPath("$.itens.length()").value(0));
        pagar();
        jdbc.update("UPDATE diaria SET status = 'contestada' WHERE id = ?", diarias.get(1));
        BigDecimal umDia = repasse();
        dinheiro(doProfissional())
                .andExpect(jsonPath("$.vaiCair").value(umDia.toPlainString()))
                .andExpect(jsonPath("$.emReclamacao").value(umDia.toPlainString()));
        minhasDiarias(doProfissional()).andExpect(jsonPath("$.itens[1].status").value("contestada"));
    }

    @Test
    void todasAsRotasExigemLoginEPapelDeProfissional() throws Exception {
        LocalDate livre = dia.plusWeeks(3);
        for (var pedido : List.of(
                get("/api/profissional/diarias"),
                get("/api/profissional/dinheiro"),
                get("/api/profissional/agenda"),
                put("/api/profissional/agenda/folgas/" + livre),
                delete("/api/profissional/agenda/folgas/" + livre))) {
            mockMvc.perform(pedido).andExpect(status().isUnauthorized());
        }
        for (var pedido : List.of(
                get("/api/profissional/diarias"),
                get("/api/profissional/dinheiro"),
                get("/api/profissional/agenda"),
                put("/api/profissional/agenda/folgas/" + livre),
                delete("/api/profissional/agenda/folgas/" + livre))) {
            mockMvc.perform(pedido.header("Authorization", doCliente())).andExpect(status().isForbidden());
        }
    }

    @Test
    void tirarFolgaForaDaJanelaERecusadoERepetirNaoMudaNada() throws Exception {
        mockMvc.perform(delete("/api/profissional/agenda/folgas/" + dia.minusYears(1))
                        .header("Authorization", doProfissional()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-invalido"));
        LocalDate livre = dia.plusWeeks(3);
        for (int vez = 0; vez < 2; vez++) {
            mockMvc.perform(delete("/api/profissional/agenda/folgas/" + livre)
                            .header("Authorization", doProfissional()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void profissionalSemPerfilRecebe404() throws Exception {
        UUID semPerfil = contas.criarConfirmada(Papel.CLIENTE).id();
        minhasDiarias(bearer(semPerfil, Papel.PROFISSIONAL)).andExpect(status().isNotFound());
    }
}
