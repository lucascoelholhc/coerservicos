package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.pagamento.ConciliacaoDiaria;
import br.com.coe.servicos.pagamento.EnvioDeEstornos;
import br.com.coe.servicos.pagamento.GatewayFalso;
import br.com.coe.servicos.usuario.Papel;

/**
 * Estornos, dinheiro sem pedido, taxas e conciliação na conta única (PA02, RN34b–RN34e). O estorno de uma diária
 * devolve exatamente o que o cliente pagou por ela (parcial, da cobrança do contrato); dinheiro recebido sem pedido
 * vira "a devolver" e volta inteiro, sem reativar o pedido; a taxa do gateway é despesa da COE; a conciliação compara o
 * saldo do gateway com o livro-caixa e abre alerta na diferença.
 */
class EstornoEConciliacaoTest extends ExecucaoDeDiariaTeste {

    @Value("${coe.gateway.token-webhook}")
    private String token;

    private UUID admin;
    private GatewayFalso gateway;
    private EnvioDeEstornos estornos;

    @Override
    protected int quantasDiarias() {
        return 2;
    }

    @BeforeEach
    void preparar() {
        admin = contas.criarConfirmada(Papel.ADMIN).id();
        gateway = contexto.getBean(GatewayFalso.class);
        estornos = contexto.getBean(EnvioDeEstornos.class);
    }

    @AfterEach
    void restaurar() {
        gateway.restaurar();
    }

    private ResultActions evento(String tipo, String corpoDoEvento) throws Exception {
        return mockMvc.perform(post("/api/webhooks/pagamento")
                .header("asaas-access-token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"event\":\"" + tipo + "\"," + corpoDoEvento + "}"));
    }

    private String cobrancaExterna(String metodo) throws Exception {
        String id = JsonPath.read(
                mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                                .header("Authorization", doCliente())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"metodo\":\"" + metodo + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.cobrancaId");
        return jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE id = ?", String.class, UUID.fromString(id));
    }

    private BigDecimal saldo(String tipoDaConta, String chave) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE l.natureza WHEN 'C' THEN l.valor ELSE -l.valor END), 0)
                  FROM lancamento l
                  JOIN transacao_financeira t ON t.id = l.transacao_id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.chave_idempotencia = ? AND r.tipo = ?
                """, BigDecimal.class, chave, tipoDaConta);
    }

    private void reclamarEDecidirAFavorDoCliente(UUID diaria) throws Exception {
        String disputa = JsonPath.read(
                mockMvc.perform(
                                post("/api/disputas")
                                        .header("Authorization", doCliente())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"diariaId\":\"" + diaria
                                                        + "\",\"motivo\":\"qualidade\",\"descricao\":\"O serviço ficou pela metade.\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id");
        mockMvc.perform(
                        post("/api/admin/disputas/" + disputa + "/decisao")
                                .header("Authorization", bearer(admin, Papel.ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"favor\":\"cliente\",\"justificativa\":\"As fotos mostram o serviço pela metade.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
            "reembolso: estorno parcial no valor exato da diária; o webhook confirma uma vez (D cliente, C gateway)")
    void estornoParcialDaDiaria() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);
        reclamarEDecidirAFavorDoCliente(diarias.get(0));
        Map<String, Object> pendente =
                jdbc.queryForMap("SELECT id, status, valor_total FROM reembolso WHERE diaria_id = ?", diarias.get(0));
        assertThat(pendente.get("status")).isEqualTo("pendente");

        estornos.executar();
        estornos.executar(); // a segunda rodada não manda de novo

        Map<String, Object> enviado = jdbc.queryForMap(
                "SELECT status, id_externo, tentativas FROM reembolso WHERE diaria_id = ?", diarias.get(0));
        assertThat(enviado.get("status")).isEqualTo("enviado");
        assertThat(((Number) enviado.get("tentativas")).intValue()).isEqualTo(1);
        String externo = (String) enviado.get("id_externo");
        String cobranca = jdbc.queryForObject(
                "SELECT c.id_externo FROM reembolso r JOIN cobranca c ON c.id = r.cobranca_id WHERE r.diaria_id = ?",
                String.class,
                diarias.get(0));
        gateway.concluirEstorno(externo);
        String corpo = "\"payment\":{\"id\":\"" + cobranca + "\"},\"refund\":{\"id\":\"" + externo + "\",\"value\":"
                + pendente.get("valor_total") + "}";
        evento("PAYMENT_PARTIALLY_REFUNDED", corpo).andExpect(status().isOk());
        evento("PAYMENT_PARTIALLY_REFUNDED", corpo).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM reembolso WHERE diaria_id = ?", String.class, diarias.get(0)))
                .isEqualTo("confirmado");
        BigDecimal valor = (BigDecimal) pendente.get("valor_total");
        assertThat(saldo("cliente", "estorno:" + diarias.get(0))).isEqualByComparingTo(valor.negate());
        assertThat(saldo("gateway", "estorno:" + diarias.get(0))).isEqualByComparingTo(valor);
        assertThat(statusDa(diarias.get(1))).isEqualTo("paga"); // a outra diária não muda
    }

    @Test
    @DisplayName("dinheiro sem pedido: vira a devolver, sai o estorno integral e o pedido nunca é reativado")
    void dinheiroSemPedido() throws Exception {
        String cartao = cobrancaExterna("cartao");
        pagar(); // pagou pelo Pix: a cobrança do cartão expira
        BigDecimal total = totalDoContrato();

        evento("PAYMENT_RECEIVED", "\"payment\":{\"id\":\"" + cartao + "\",\"value\":" + total + "}")
                .andExpect(status().isOk());

        UUID cobrancaDoCartao = jdbc.queryForObject("SELECT id FROM cobranca WHERE id_externo = ?", UUID.class, cartao);
        Map<String, Object> devolucao = jdbc.queryForMap(
                "SELECT id, status, valor, motivo FROM devolucao WHERE cobranca_id = ?", cobrancaDoCartao);
        assertThat(devolucao.get("status")).isEqualTo("pendente");
        assertThat((BigDecimal) devolucao.get("valor")).isEqualByComparingTo(total);
        assertThat(devolucao.get("motivo")).isEqualTo("contrato-ja-pago");
        assertThat(saldo("a_devolver", "sem-pedido:" + cobrancaDoCartao)).isEqualByComparingTo(total);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM alerta_financeiro WHERE tipo = 'dinheiro-sem-pedido' AND referencia = ?",
                        Integer.class,
                        devolucao.get("id")))
                .isEqualTo(1);

        estornos.executar();
        String externo = jdbc.queryForObject(
                "SELECT id_externo FROM devolucao WHERE cobranca_id = ?", String.class, cobrancaDoCartao);
        gateway.concluirEstorno(externo);
        evento(
                        "PAYMENT_REFUNDED",
                        "\"payment\":{\"id\":\"" + cartao + "\"},\"refund\":{\"id\":\"" + externo + "\",\"value\":"
                                + total + "}")
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM devolucao WHERE cobranca_id = ?", String.class, cobrancaDoCartao))
                .isEqualTo("confirmado");
        assertThat(saldo("a_devolver", "devolucao:" + cobrancaDoCartao)).isEqualByComparingTo(total.negate());
        assertThat(jdbc.queryForObject("SELECT status FROM contrato WHERE id = ?", String.class, contrato))
                .isEqualTo("pago");
    }

    @Test
    @DisplayName("taxa do gateway (> 0) é despesa da COE no pagamento e no repasse; o repasse do profissional não muda")
    void taxaEDespesaDaCoe() throws Exception {
        jdbc.update("""
                INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
                 ('TAXA_GATEWAY_PIX', '1.99', 'decimal', 'teste de taxa', now() - interval '1 second'),
                 ('TAXA_GATEWAY_TRANSFERENCIA', '0.50', 'decimal', 'teste de taxa', now() - interval '1 second')""");
        configuracao.invalidarCache();
        try {
            pagar();
            UUID cobranca = jdbc.queryForObject(
                    "SELECT id FROM cobranca WHERE contrato_id = ? AND status = 'confirmada'", UUID.class, contrato);
            assertThat(saldo("despesa_coe", "taxa:" + cobranca)).isEqualByComparingTo("-1.99");
            assertThat(saldo("gateway", "taxa:" + cobranca)).isEqualByComparingTo("1.99");
        } finally {
            jdbc.update("""
                    INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
                     ('TAXA_GATEWAY_PIX', '0.00', 'decimal', 'volta ao padrão', now()),
                     ('TAXA_GATEWAY_TRANSFERENCIA', '0.00', 'decimal', 'volta ao padrão', now())""");
            configuracao.invalidarCache();
        }
        BigDecimal valorDia =
                jdbc.queryForObject("SELECT valor FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                        SELECT r.valor FROM repasse r JOIN repasse_diaria rd ON rd.repasse_id = r.id
                         WHERE rd.diaria_id = ?""", BigDecimal.class, diarias.get(0))).isEqualByComparingTo(valorDia);
    }

    @Test
    @DisplayName("conciliação: diferença entre o saldo do gateway e o livro-caixa abre alerta e auditoria")
    void conciliacao() throws Exception {
        pagar();
        ConciliacaoDiaria conciliacao = contexto.getBean(ConciliacaoDiaria.class);
        BigDecimal antes = conciliacao.calcular().diferenca();
        gateway.simularDiferencaNoSaldo(new BigDecimal("-10.00"));

        conciliacao.executar();

        List<Map<String, Object>> alertas = jdbc.queryForList(
                "SELECT detalhe->>'diferenca' AS diferenca FROM alerta_financeiro WHERE tipo = 'conciliacao'"
                        + " ORDER BY criado_em DESC LIMIT 1");
        assertThat(new BigDecimal((String) alertas.get(0).get("diferenca")))
                .isEqualByComparingTo(antes.add(new BigDecimal("-10.00")));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'financeiro.conciliacao-divergente'",
                        Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("conciliação sem diferença não abre alerta")
    void conciliacaoSemDiferenca() {
        assertThat(ConciliacaoDiaria.divergente(new BigDecimal("100.00"), new BigDecimal("100.00")))
                .isFalse();
        assertThat(ConciliacaoDiaria.divergente(new BigDecimal("100.00"), new BigDecimal("99.99")))
                .isTrue();
    }
}
