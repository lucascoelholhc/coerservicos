package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
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
import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.TotaisContrato;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DOM-06 (dia 25): contrato com diárias, total conferido, comissão congelada e endereço cifrado (RN24–RN33). */
class ContratoTest extends IntegracaoTest {

    private static final int ITAJAI = 4208203;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    @Autowired
    private Clock clock;

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado profissional;
    private UUID cliente;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
    }

    /** Os próximos dias úteis (o profissional de teste trabalha de segunda a sexta), a partir de amanhã. */
    private List<LocalDate> diasUteis(int quantos) {
        LocalDate dia = FusoDeNegocio.hoje(clock).plusDays(1);
        List<LocalDate> dias = new java.util.ArrayList<>();
        while (dias.size() < quantos) {
            if (dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dias.add(dia);
            }
            dia = dia.plusDays(1);
        }
        return dias;
    }

    private LocalDate proximo(DayOfWeek diaDaSemana) {
        LocalDate dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != diaDaSemana) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private static String pedido(UUID perfil, String dias, String extra) {
        return """
                {"profissionalId":"%s","dias":[%s],"servicos":["Corte de grama"],"material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":%d,"logradouro":"Rua das Palmeiras","numero":"120",
                             "bairro":"Centro","complemento":"casa 2"},
                 "versaoTermosAceita":"%s"%s}""".formatted(perfil, dias, ITAJAI, "%TERMOS%", extra);
    }

    private static String dia(LocalDate data, String tipo) {
        return "{\"data\":\"" + data + "\",\"tipo\":\"" + tipo + "\"}";
    }

    private ResultActions contratar(UUID quem, String corpo) throws Exception {
        return mockMvc.perform(post("/api/contratos")
                .header("Authorization", bearer(quem, Papel.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo.replace("%TERMOS%", configuracao.versaoTermos())));
    }

    @Test
    @DisplayName("contrato com duas diárias: total pela CalculadoraDiaria, comissão congelada, endereço cifrado")
    void contrata() throws Exception {
        List<LocalDate> dias = diasUteis(2);
        TotaisContrato esperado = TotaisContrato.somar(List.of(
                CalculadoraDiaria.calcular(Dinheiro.de("200.00"), configuracao.comissao(), configuracao.taxaPagaPor()),
                CalculadoraDiaria.calcular(
                        Dinheiro.de("200.00"), configuracao.comissao(), configuracao.taxaPagaPor())));

        String corpo = contratar(
                        cliente,
                        pedido(
                                profissional.profissional(),
                                dia(dias.get(0), "inteira") + "," + dia(dias.get(1), "inteira"),
                                ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("aguardando_pagamento"))
                .andExpect(jsonPath("$.dias", hasSize(2)))
                .andExpect(jsonPath("$.dias[0].status").value("agendada"))
                .andExpect(jsonPath("$.valorServicos")
                        .value(esperado.valorServicos().toString()))
                .andExpect(jsonPath("$.valorComissao").value(esperado.comissao().toString()))
                .andExpect(
                        jsonPath("$.valorTotal").value(esperado.totalCliente().toString()))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua das Palmeiras"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(corpo, "$.id");

        assertThat(jdbc.queryForObject(
                        "SELECT valor_total FROM contrato WHERE id = ?::uuid", java.math.BigDecimal.class, id))
                .isEqualByComparingTo(esperado.totalCliente().valor());
        assertThat(jdbc.queryForObject(
                        "SELECT comissao_pct FROM contrato WHERE id = ?::uuid", java.math.BigDecimal.class, id))
                .isEqualByComparingTo(configuracao.comissao().valor());
        byte[] endereco =
                jdbc.queryForObject("SELECT endereco_cifrado FROM contrato WHERE id = ?::uuid", byte[].class, id);
        assertThat(new String(endereco, StandardCharsets.ISO_8859_1)).doesNotContain("Palmeiras");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM historico_diaria h JOIN diaria d ON d.id = h.diaria_id"
                                + " WHERE d.contrato_id = ?::uuid AND h.para_status = 'agendada'",
                        Integer.class,
                        id))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("dia já ocupado por outro contrato: 409 dia-indisponivel")
    void diaOcupado() throws Exception {
        LocalDate data = diasUteis(1).get(0);
        contratar(cliente, pedido(profissional.profissional(), dia(data, "inteira"), ""))
                .andExpect(status().isCreated());
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();

        contratar(outro, pedido(profissional.profissional(), dia(data, "inteira"), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-indisponivel"));
    }

    @Test
    @DisplayName("dia que ele não trabalha, hoje ou além de 4 semanas, e meia sem oferta: 422")
    void diasInvalidos() throws Exception {
        contratar(cliente, pedido(profissional.profissional(), dia(proximo(DayOfWeek.SATURDAY), "inteira"), ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-fora-da-agenda"));
        contratar(cliente, pedido(profissional.profissional(), dia(FusoDeNegocio.hoje(clock), "inteira"), ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-fora-da-janela"));
        contratar(
                        cliente,
                        pedido(
                                profissional.profissional(),
                                dia(FusoDeNegocio.hoje(clock).plusDays(40), "inteira"),
                                ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-fora-da-janela"));
        contratar(cliente, pedido(profissional.profissional(), dia(diasUteis(1).get(0), "meia"), ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:meia-diaria-indisponivel"));
    }

    @Test
    @DisplayName("conta em análise não contrata (DOM-05): 403 conta-em-analise; sem login 401")
    void contaEmAnalise() throws Exception {
        jdbc.update("UPDATE usuario SET status = 'em_analise' WHERE id = ?", cliente);

        contratar(cliente, pedido(profissional.profissional(), dia(diasUteis(1).get(0), "inteira"), ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-em-analise"));
        mockMvc.perform(post("/api/contratos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ver o contrato: o cliente vê o endereço; o profissional não, antes do pagamento (RN29); outro, 404")
    void verContrato() throws Exception {
        String id = JsonPath.read(
                contratar(
                                cliente,
                                pedido(
                                        profissional.profissional(),
                                        dia(diasUteis(1).get(0), "inteira"),
                                        ""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");

        mockMvc.perform(get("/api/contratos/" + id).header("Authorization", bearer(cliente, Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.numero").value("120"))
                .andExpect(jsonPath("$.cep").value("88301000"));
        mockMvc.perform(get("/api/contratos/" + id)
                        .header("Authorization", bearer(profissional.usuario(), Papel.CLIENTE, Papel.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco").doesNotExist())
                .andExpect(jsonPath("$.cep").doesNotExist())
                .andExpect(jsonPath("$.cidade.codigoIbge").value(ITAJAI));
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        String alheio = mockMvc.perform(
                        get("/api/contratos/" + id).header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String inexistente = mockMvc.perform(get("/api/contratos/" + UUID.randomUUID())
                        .header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(semRequisicao(alheio).replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(semRequisicao(inexistente).replaceAll("\"instance\":\"[^\"]*\"", ""));
        mockMvc.perform(get("/api/contratos/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("telefone no complemento ou no bairro é recusado (chegaria ao profissional); dia nulo = 400")
    void enderecoSemContato() throws Exception {
        String base = pedido(profissional.profissional(), dia(diasUteis(1).get(0), "inteira"), "");
        contratar(cliente, base.replace("\"casa 2\"", "\"liga 47 99123-4567\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        contratar(cliente, base.replace("\"Centro\"", "\"zap 47991234567\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        contratar(cliente, pedido(profissional.profissional(), "null", "")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrato WHERE cliente_id = ?", Integer.class, cliente))
                .isZero();
    }

    @Test
    @DisplayName("pedido sem serviço e sem descrição de 15+ letras (RN27) e termos antigos: 422")
    void pedidoInvalido() throws Exception {
        String semServico = pedido(profissional.profissional(), dia(diasUteis(1).get(0), "inteira"), "")
                .replace("\"servicos\":[\"Corte de grama\"]", "\"servicos\":[]");
        contratar(cliente, semServico)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:pedido-incompleto"));
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", bearer(cliente, Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(
                                        profissional.profissional(),
                                        dia(diasUteis(1).get(0), "inteira"),
                                        "")
                                .replace("%TERMOS%", "0.0-antiga")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:termos-desatualizados"));
    }
}
