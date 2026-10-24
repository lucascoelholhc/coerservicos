package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
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

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DOM-06 (dia 26), RN32: contrato não pago no prazo expira, as diárias são canceladas e o dia volta a ficar livre. */
class ExpiracaoDeContratosTest extends IntegracaoTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    @Autowired
    private ExpiracaoDeContratos expiracao;

    @Autowired
    private Clock clock;

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado profissional;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
    }

    private LocalDate proximaSegunda() {
        LocalDate dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.MONDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private UUID contratar(UUID cliente, LocalDate dia, int esperado) throws Exception {
        String corpo = """
                {"profissionalId":"%s","dias":[{"data":"%s","tipo":"inteira"}],"servicos":["Corte de grama"],
                 "material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":4208203,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(profissional.profissional(), dia, configuracao.versaoTermos());
        String resposta = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", bearer(cliente, Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().is(esperado))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return esperado == 201 ? UUID.fromString(JsonPath.read(resposta, "$.id")) : null;
    }

    private String statusDo(UUID contrato) {
        return jdbc.queryForObject("SELECT status FROM contrato WHERE id = ?", String.class, contrato);
    }

    @Test
    @DisplayName(
            "antes do prazo nada muda; depois, expirado + diárias canceladas com histórico; rodar de novo não repete")
    void expira() throws Exception {
        UUID cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID contrato = contratar(cliente, proximaSegunda(), 201);
        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", bearer(cliente, Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"pix\"}"))
                .andExpect(status().isCreated());

        relogio.avancar(configuracao.expiracaoPagamento().minusSeconds(1));
        expiracao.executar();
        assertThat(statusDo(contrato)).isEqualTo("aguardando_pagamento");

        relogio.avancar(Duration.ofSeconds(1));
        expiracao.executar();
        expiracao.executar();

        assertThat(statusDo(contrato)).isEqualTo("expirado");
        assertThat(jdbc.queryForList("SELECT status FROM cobranca WHERE contrato_id = ?", String.class, contrato))
                .containsExactly("expirada");
        assertThat(jdbc.queryForList(
                        "SELECT status FROM diaria WHERE contrato_id = ? AND cancelada_em IS NOT NULL",
                        String.class,
                        contrato))
                .containsExactly("cancelada");
        List<String> historico = jdbc.queryForList("""
                SELECT h.para_status || '/' || h.origem || '/' || coalesce(h.motivo, '-') FROM historico_diaria h
                  JOIN diaria d ON d.id = h.diaria_id
                 WHERE d.contrato_id = ? ORDER BY h.id
                """, String.class, contrato);
        assertThat(historico).containsExactly("agendada/cliente/-", "cancelada/sistema/pagamento-expirado");
    }

    @Test
    @DisplayName("o dia do contrato expirado volta a ficar livre para outro cliente")
    void diaVoltaALivre() throws Exception {
        LocalDate segunda = proximaSegunda().plusWeeks(1);
        UUID primeiro = contratar(contas.criarConfirmada(Papel.CLIENTE).id(), segunda, 201);
        UUID outroCliente = contas.criarConfirmada(Papel.CLIENTE).id();
        contratar(outroCliente, segunda, 409);

        relogio.avancar(configuracao.expiracaoPagamento());
        expiracao.executar();

        assertThat(statusDo(primeiro)).isEqualTo("expirado");
        assertThat(statusDo(contratar(outroCliente, segunda, 201))).isEqualTo("aguardando_pagamento");
    }
}
