package br.com.coe.servicos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * DOM-07 (dia 28): webhook do gateway no formato do Asaas (token no header {@code asaas-access-token}, evento
 * {@code {id, event, payment{id, value, status}}}). Só token válido ocupa o id do evento; repetido não duplica nada;
 * o pagamento confirmado vira o contrato pago, as diárias pagas, o contato liberado e uma transação balanceada no
 * livro-caixa (D gateway, C custódia).
 */
class WebhookTest extends IntegracaoTest {

    private static final AtomicLong EVENTOS = new AtomicLong();

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

    @Value("${coe.gateway.token-webhook}")
    private String token;

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado profissional;
    private UUID cliente;
    private UUID contrato;

    @BeforeEach
    void preparar() throws Exception {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        contrato = contratar();
    }

    private UUID contratar() throws Exception {
        LocalDate dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            dia = dia.plusDays(1);
        }
        String corpo = """
                {"profissionalId":"%s","dias":[{"data":"%s","tipo":"inteira"},{"data":"%s","tipo":"inteira"}],
                 "servicos":["Corte de grama"],"material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":4208203,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(profissional.profissional(), dia, dia.plusDays(1), configuracao.versaoTermos());
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/contratos")
                                .header("Authorization", bearer(cliente, Papel.CLIENTE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id"));
    }

    /** Pede a cobrança e devolve o id dela no gateway ("pay_..."). */
    private String cobrar(String metodo) throws Exception {
        String cobranca = JsonPath.read(
                mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                                .header("Authorization", bearer(cliente, Papel.CLIENTE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"metodo\":\"" + metodo + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.cobrancaId");
        return jdbc.queryForObject(
                "SELECT id_externo FROM cobranca WHERE id = ?", String.class, UUID.fromString(cobranca));
    }

    private BigDecimal total() {
        return jdbc.queryForObject("SELECT valor_total FROM contrato WHERE id = ?", BigDecimal.class, contrato);
    }

    private static String novoEvento() {
        return "evt_teste_" + EVENTOS.incrementAndGet() + "_" + UUID.randomUUID();
    }

    private static String evento(String id, String tipo, String pagamento, BigDecimal valor) {
        return """
                {"id":"%s","event":"%s","dateCreated":"2026-10-05 09:00:00",
                 "payment":{"object":"payment","id":"%s","value":%s,"status":"RECEIVED","billingType":"PIX",
                            "campoNovoDoGateway":{"qualquer":true}}}""".formatted(id, tipo, pagamento, valor.toPlainString());
    }

    private ResultActions enviar(String corpo, String comToken) throws Exception {
        var pedido = post("/api/webhooks/pagamento")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo.getBytes(StandardCharsets.UTF_8));
        if (comToken != null) {
            pedido.header("asaas-access-token", comToken);
        }
        return mockMvc.perform(pedido);
    }

    private String statusDoContrato() {
        return jdbc.queryForObject("SELECT status FROM contrato WHERE id = ?", String.class, contrato);
    }

    private int transacoesDoContrato() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM transacao_financeira WHERE contrato_id = ?", Integer.class, contrato);
    }

    @Test
    @DisplayName(
            "pagamento recebido: contrato pago, diárias pagas com histórico, cobrança confirmada, contato liberado")
    void pagamentoRecebido() throws Exception {
        String pagamento = cobrar("pix");
        String id = novoEvento();

        enviar(evento(id, "PAYMENT_RECEIVED", pagamento, total()), token).andExpect(status().isOk());

        Map<String, Object> c =
                jdbc.queryForMap("SELECT status, pago_em, contato_liberado_em FROM contrato WHERE id = ?", contrato);
        assertThat(c.get("status")).isEqualTo("pago");
        assertThat(c.get("pago_em")).isNotNull();
        assertThat(c.get("contato_liberado_em")).isNotNull();
        assertThat(jdbc.queryForList("SELECT status FROM diaria WHERE contrato_id = ?", String.class, contrato))
                .containsOnly("paga")
                .hasSize(2);
        assertThat(jdbc.queryForList("""
                        SELECT h.de_status || '>' || h.para_status || '/' || h.origem FROM historico_diaria h
                          JOIN diaria d ON d.id = h.diaria_id
                         WHERE d.contrato_id = ? AND h.para_status = 'paga'
                        """, String.class, contrato))
                .containsOnly("agendada>paga/gateway")
                .hasSize(2);
        assertThat(jdbc.queryForMap("SELECT status, confirmada_em FROM cobranca WHERE id_externo = ?", pagamento)
                        .get("status"))
                .isEqualTo("confirmada");
        assertThat(jdbc.queryForMap(
                        "SELECT assinatura_valida, processado_em, erro FROM evento_gateway WHERE id_evento = ?", id))
                .containsEntry("assinatura_valida", true)
                .containsEntry("erro", null);

        // o profissional vê o endereço depois do pagamento (RN29)
        mockMvc.perform(get("/api/contratos/" + contrato)
                        .header("Authorization", bearer(profissional.usuario(), Papel.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pago"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua das Palmeiras"));
    }

    @Test
    @DisplayName("livro-caixa: uma transação de pagamento balanceada (D gateway = C custódia = total pago)")
    void livroCaixa() throws Exception {
        String pagamento = cobrar("pix");
        enviar(evento(novoEvento(), "PAYMENT_RECEIVED", pagamento, total()), token)
                .andExpect(status().isOk());

        List<Map<String, Object>> lancamentos = jdbc.queryForList("""
                SELECT r.tipo, l.natureza, l.valor FROM lancamento l
                  JOIN transacao_financeira t ON t.id = l.transacao_id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.contrato_id = ? AND t.tipo = 'pagamento'
                 ORDER BY l.natureza DESC
                """, contrato);
        assertThat(lancamentos).hasSize(2);
        assertThat(lancamentos.get(0)).containsEntry("tipo", "gateway").containsEntry("natureza", "D");
        assertThat(lancamentos.get(1)).containsEntry("tipo", "custodia").containsEntry("natureza", "C");
        assertThat(lancamentos)
                .allSatisfy(l -> assertThat((BigDecimal) l.get("valor")).isEqualByComparingTo(total()));
        assertThat(contexto.getBean(LivroCaixa.class).saldoDeCustodia(contrato).valor())
                .isEqualByComparingTo(total());
    }

    @Test
    @DisplayName("evento repetido e CONFIRMED + RECEIVED da mesma cobrança: nada é duplicado")
    void idempotente() throws Exception {
        String pagamento = cobrar("cartao");
        String id = novoEvento();
        String corpo = evento(id, "PAYMENT_CONFIRMED", pagamento, total());

        enviar(corpo, token).andExpect(status().isOk());
        enviar(corpo, token).andExpect(status().isOk());
        enviar(evento(novoEvento(), "PAYMENT_RECEIVED", pagamento, total()), token)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM evento_gateway WHERE id_evento = ? AND assinatura_valida",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(transacoesDoContrato()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM historico_diaria h JOIN diaria d ON d.id = h.diaria_id
                         WHERE d.contrato_id = ? AND h.para_status = 'paga'
                        """, Integer.class, contrato)).isEqualTo(2);
    }

    @Test
    @DisplayName("token errado ou ausente: 401, guardado como inválido, não ocupa o id e não muda nada")
    void tokenInvalido() throws Exception {
        String pagamento = cobrar("pix");
        String id = novoEvento();

        enviar(evento(id, "PAYMENT_RECEIVED", pagamento, total()), "token-errado")
                .andExpect(status().isUnauthorized());
        enviar(evento(id, "PAYMENT_RECEIVED", pagamento, total()), null).andExpect(status().isUnauthorized());

        assertThat(statusDoContrato()).isEqualTo("aguardando_pagamento");
        assertThat(jdbc.queryForList(
                        "SELECT assinatura_valida FROM evento_gateway WHERE id_evento = ?", Boolean.class, id))
                .containsOnly(false)
                .hasSize(2);

        // o mesmo id com token certo ainda é processado (o forjado não o ocupou)
        enviar(evento(id, "PAYMENT_RECEIVED", pagamento, total()), token).andExpect(status().isOk());
        assertThat(statusDoContrato()).isEqualTo("pago");
    }

    @Test
    @DisplayName("corpo que não é JSON ou sem id/event: 400 sem gravar")
    void corpoInvalido() throws Exception {
        enviar("isto não é json", token).andExpect(status().isBadRequest());
        enviar("{\"event\":\"PAYMENT_RECEIVED\"}", token).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("valor diferente do cobrado: registrado com erro, contrato continua esperando")
    void valorDivergente() throws Exception {
        String pagamento = cobrar("pix");
        String id = novoEvento();

        enviar(evento(id, "PAYMENT_RECEIVED", pagamento, total().subtract(BigDecimal.ONE)), token)
                .andExpect(status().isOk());

        assertThat(statusDoContrato()).isEqualTo("aguardando_pagamento");
        assertThat(jdbc.queryForObject("SELECT erro FROM evento_gateway WHERE id_evento = ?", String.class, id))
                .isEqualTo("valor-divergente");
        assertThat(transacoesDoContrato()).isZero();
    }

    @Test
    @DisplayName("pagamento de contrato já pago por outra cobrança: registrado com erro e auditado, sem efeito")
    void contratoSemPagamentoPendente() throws Exception {
        String pix = cobrar("pix");
        String cartao = cobrar("cartao");
        enviar(evento(novoEvento(), "PAYMENT_RECEIVED", pix, total()), token).andExpect(status().isOk());

        String segundo = novoEvento();
        enviar(evento(segundo, "PAYMENT_CONFIRMED", cartao, total()), token).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT erro FROM evento_gateway WHERE id_evento = ?", String.class, segundo))
                .isEqualTo("contrato-ja-pago");
        assertThat(transacoesDoContrato()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM log_auditoria
                         WHERE acao = 'pagamento.sem-contrato-pendente' AND entidade_id = ?
                        """, Integer.class, contrato)).isEqualTo(1);
    }

    @Test
    @DisplayName("cobrança desconhecida: registrada com erro; vencida (OVERDUE): cobrança expirada")
    void desconhecidaEVencida() throws Exception {
        String id = novoEvento();
        enviar(evento(id, "PAYMENT_RECEIVED", "pay_nao_existe", BigDecimal.TEN), token)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT erro FROM evento_gateway WHERE id_evento = ?", String.class, id))
                .isEqualTo("cobranca-desconhecida");

        String pagamento = cobrar("pix");
        enviar(evento(novoEvento(), "PAYMENT_OVERDUE", pagamento, total()), token)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM cobranca WHERE id_externo = ?", String.class, pagamento))
                .isEqualTo("expirada");
        assertThat(statusDoContrato()).isEqualTo("aguardando_pagamento");
    }

    @Test
    @DisplayName("a conversa entre cliente e profissional fica com o contato liberado (RN50)")
    void conversaLiberada() throws Exception {
        String conversa = JsonPath.read(
                mockMvc.perform(post("/api/conversas")
                                .header("Authorization", bearer(cliente, Papel.CLIENTE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"profissionalId\":\"" + profissional.profissional() + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");
        String pagamento = cobrar("pix");

        enviar(evento(novoEvento(), "PAYMENT_RECEIVED", pagamento, total()), token)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT contato_liberado_em FROM conversa WHERE id = ?",
                        Object.class,
                        UUID.fromString(conversa)))
                .isNotNull();
    }
}
