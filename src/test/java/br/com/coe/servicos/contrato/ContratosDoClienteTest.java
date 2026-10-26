package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao;
import br.com.coe.servicos.usuario.Papel;

/**
 * FE-05 (dia 33): o que a área do cliente precisa da API — a lista dos meus contratos, o profissional e o prazo da
 * liberação no detalhe, o pagamento simulado com o gateway falso (só local e test) e as fotos do dia.
 */
class ContratosDoClienteTest extends ExecucaoDeDiariaTeste {

    @Test
    @DisplayName("GET /api/contratos: só os meus, do mais novo, com o profissional em Primeiro S. e as diárias")
    void lista() throws Exception {
        mockMvc.perform(get("/api/contratos").header("Authorization", doCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id").value(contrato.toString()))
                .andExpect(jsonPath("$.itens[0].status").value("aguardando_pagamento"))
                .andExpect(jsonPath("$.itens[0].profissional.nome").value(NomeDeExibicao.de(profissional.nome())))
                .andExpect(jsonPath("$.itens[0].profissional.profissao").value("Jardineiro"))
                .andExpect(jsonPath("$.itens[0].dias", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].valorTotal").isNotEmpty());

        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        mockMvc.perform(get("/api/contratos").header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)));
        mockMvc.perform(get("/api/contratos").header("Authorization", doProfissional()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contratos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("detalhe: profissional em Primeiro S. antes do pagamento e o prazo da liberação no dia aguardando")
    void detalhe() throws Exception {
        mockMvc.perform(get("/api/contratos/" + contrato).header("Authorization", doCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissional.id")
                        .value(profissional.profissional().toString()))
                .andExpect(jsonPath("$.profissional.nome").value(NomeDeExibicao.de(profissional.nome())))
                .andExpect(jsonPath("$.dias[0].autoLiberaEm").doesNotExist());

        pagar();
        trabalharODia(diarias.get(0), dia);
        mockMvc.perform(get("/api/contratos/" + contrato).header("Authorization", doCliente()))
                .andExpect(jsonPath("$.dias[0].status").value("aguardando"))
                .andExpect(jsonPath("$.dias[0].autoLiberaEm").isNotEmpty());
    }

    @Test
    @DisplayName("pagamento simulado (gateway falso): o contrato vira pago pelo mesmo webhook; sem cobrança = 422")
    void simular() throws Exception {
        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento/simular")
                        .header("Authorization", doCliente()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:sem-cobranca-pendente"));

        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"pix\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulavel").value(true));
        String outro = bearer(contas.criarConfirmada(Papel.CLIENTE).id(), Papel.CLIENTE);
        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento/simular")
                        .header("Authorization", outro))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento/simular")
                        .header("Authorization", doCliente()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM contrato WHERE id = ?", String.class, contrato))
                .isEqualTo("pago");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM evento_gateway e JOIN cobranca c ON c.id_externo = e.payload->'payment'->>'id'"
                                + " WHERE c.contrato_id = ? AND e.assinatura_valida",
                        Integer.class,
                        contrato))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("fotos do dia: cliente e profissional da diária veem por URL assinada; outro = 404")
    void fotos() throws Exception {
        pagar();
        UUID diaria = diarias.get(0);
        trabalharODia(diaria, dia);

        mockMvc.perform(get("/api/diarias/" + diaria + "/fotos").header("Authorization", doCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos", hasSize(1)))
                .andExpect(jsonPath("$.fotos[0].url").value(startsWith("http")));
        mockMvc.perform(get("/api/diarias/" + diaria + "/fotos").header("Authorization", doProfissional()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos", hasSize(1)));
        String outro = bearer(contas.criarConfirmada(Papel.CLIENTE).id(), Papel.CLIENTE);
        mockMvc.perform(get("/api/diarias/" + diaria + "/fotos").header("Authorization", outro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/diarias/" + diaria + "/fotos")).andExpect(status().isUnauthorized());
    }
}
