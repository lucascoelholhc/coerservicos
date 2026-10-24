package br.com.coe.servicos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
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
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * DOM-07 (dia 27): cobrança do contrato pelo {@code GatewayPagamento} (adaptador falso no formato do Asaas). O valor
 * sai do contrato no banco; cartão só por link do gateway (a COE nunca vê dado de cartão).
 */
class CobrancaTest extends IntegracaoTest {

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
    private UUID contrato;

    @BeforeEach
    void preparar() throws Exception {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        contrato = contratar(cliente);
    }

    private UUID contratar(UUID quem) throws Exception {
        LocalDate dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.TUESDAY) {
            dia = dia.plusDays(1);
        }
        String corpo = """
                {"profissionalId":"%s","dias":[{"data":"%s","tipo":"inteira"}],"servicos":["Corte de grama"],
                 "material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":4208203,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(profissional.profissional(), dia, configuracao.versaoTermos());
        String resposta = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", bearer(quem, Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(resposta, "$.id"));
    }

    private ResultActions pagar(UUID quem, UUID id, String metodo) throws Exception {
        return mockMvc.perform(post("/api/contratos/" + id + "/pagamento")
                .header("Authorization", bearer(quem, Papel.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metodo\":\"" + metodo + "\"}"));
    }

    private static String corpo(ResultActions resposta) throws Exception {
        return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Pix: cobrança criada no gateway com o total do contrato, copia-e-cola e o prazo do contrato")
    void pix() throws Exception {
        Map<String, Object> esperado =
                jdbc.queryForMap("SELECT valor_total, pagamento_expira_em FROM contrato WHERE id = ?", contrato);

        String resposta = corpo(pagar(cliente, contrato, "pix")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metodo").value("pix"))
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(jsonPath("$.valor").value(esperado.get("valor_total").toString()))
                .andExpect(jsonPath("$.pixCopiaECola").isNotEmpty())
                .andExpect(jsonPath("$.linkPagamento").isNotEmpty()));

        UUID cobranca = UUID.fromString(JsonPath.read(resposta, "$.cobrancaId"));
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT gateway, id_externo, metodo, valor, status, expira_em FROM cobranca WHERE id = ?", cobranca);
        assertThat(linha.get("gateway")).isEqualTo("falso");
        assertThat((String) linha.get("id_externo")).startsWith("pay_");
        assertThat(linha.get("valor")).isEqualTo(esperado.get("valor_total"));
        assertThat(linha.get("expira_em")).isEqualTo(esperado.get("pagamento_expira_em"));
    }

    @Test
    @DisplayName("pedir de novo com o mesmo método devolve a mesma cobrança; cartão só tem link (sem copia-e-cola)")
    void reaproveitaECartao() throws Exception {
        String primeira =
                JsonPath.read(corpo(pagar(cliente, contrato, "pix").andExpect(status().isCreated())), "$.cobrancaId");
        pagar(cliente, contrato, "pix")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobrancaId").value(primeira));

        pagar(cliente, contrato, "cartao")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metodo").value("cartao"))
                .andExpect(jsonPath("$.pixCopiaECola").doesNotExist())
                .andExpect(jsonPath("$.linkPagamento").value(org.hamcrest.Matchers.startsWith("http")));

        mockMvc.perform(get("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", bearer(cliente, Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("cartao"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE contrato_id = ?", Integer.class, contrato))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("contrato de outro cliente = 404 igual a inexistente; sem papel CLIENTE = 403; sem token = 401")
    void antiIdor() throws Exception {
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        String alheio = corpo(pagar(outro, contrato, "pix").andExpect(status().isNotFound()));
        String inexistente = corpo(pagar(outro, UUID.randomUUID(), "pix").andExpect(status().isNotFound()));
        assertThat(semRequisicao(alheio).replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(semRequisicao(inexistente).replaceAll("\"instance\":\"[^\"]*\"", ""));
        mockMvc.perform(get("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", bearer(outro, Papel.CLIENTE)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", bearer(profissional.usuario(), Papel.PROFISSIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"pix\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"pix\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE contrato_id = ?", Integer.class, contrato))
                .isZero();
    }

    @Test
    @DisplayName("sem cobrança ainda = 404; método desconhecido = 400; contrato expirado não cobra (422)")
    void casosDeErro() throws Exception {
        mockMvc.perform(get("/api/contratos/" + contrato + "/pagamento")
                        .header("Authorization", bearer(cliente, Papel.CLIENTE)))
                .andExpect(status().isNotFound());
        pagar(cliente, contrato, "boleto").andExpect(status().isBadRequest());

        jdbc.update("UPDATE contrato SET status = 'expirado' WHERE id = ?", contrato);
        pagar(cliente, contrato, "pix")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contrato-sem-pagamento-pendente"));
    }
}
