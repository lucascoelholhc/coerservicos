package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.compartilhado.cripto.Campo;
import br.com.coe.servicos.compartilhado.cripto.CifraDeCampo;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.pagamento.EnvioDeRepasses;
import br.com.coe.servicos.pagamento.GatewayFalso;
import br.com.coe.servicos.pagamento.GatewayPagamento;
import br.com.coe.servicos.usuario.Papel;

/**
 * Repasse por Pix na conta única (PA02, RN34a, RN15): na liberação nasce um repasse pendente por diária; só sai
 * depois de o admin conferir a chave Pix; a transferência leva o id da diária como referência (consulta antes de
 * mandar de novo); o webhook de transferência confirma (livro-caixa: D profissional, C gateway) ou falha (alerta no
 * painel e nova tentativa manual, auditada). A chave em claro nunca vai para o log.
 */
@ExtendWith(OutputCaptureExtension.class)
class RepasseTest extends ExecucaoDeDiariaTeste {

    @Value("${coe.gateway.token-webhook}")
    private String token;

    private UUID admin;
    private GatewayFalso gateway;
    private EnvioDeRepasses envio;

    @BeforeEach
    void preparar() throws Exception {
        admin = contas.criarConfirmada(Papel.ADMIN).id();
        gateway = contexto.getBean(GatewayFalso.class);
        envio = contexto.getBean(EnvioDeRepasses.class);
        pagar();
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
    }

    @AfterEach
    void restaurarGateway() {
        gateway.restaurar();
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    private Map<String, Object> repasse() {
        return jdbc.queryForMap("""
                SELECT r.id, r.status, r.valor, r.id_externo, r.erro, r.tentativas, r.transacao_id
                  FROM repasse r JOIN repasse_diaria rd ON rd.repasse_id = r.id
                 WHERE rd.diaria_id = ?
                """, diarias.get(0));
    }

    private ResultActions conferirPix() throws Exception {
        return mockMvc.perform(post("/api/admin/profissionais/" + profissional.profissional() + "/pix/conferido")
                .header("Authorization", doAdmin()));
    }

    private ResultActions eventoDeTransferencia(String evento, String idExterno, String motivo) throws Exception {
        String falha = motivo == null ? "" : ",\"failReason\":\"" + motivo + "\"";
        String situacao = evento.equals("TRANSFER_DONE") ? "DONE" : "FAILED";
        String corpo = "{\"id\":\"evt_tra_" + UUID.randomUUID() + "\",\"event\":\"" + evento
                + "\",\"transfer\":{\"id\":\"" + idExterno + "\",\"status\":\"" + situacao + "\"" + falha + "}}";
        return mockMvc.perform(post("/api/webhooks/pagamento")
                .header("asaas-access-token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private BigDecimal saldoDoProfissional() {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(saldo), 0) FROM saldo_conta WHERE tipo = 'profissional' AND profissional_id = ?",
                BigDecimal.class,
                profissional.profissional());
    }

    private String chaveEmClaro() {
        byte[] cifrada = jdbc.queryForObject(
                "SELECT chave_pix_cifrada FROM profissional WHERE id = ?", byte[].class, profissional.profissional());
        return contexto.getBean(CifraDeCampo.class).decifrar(cifrada, Campo.CHAVE_PIX);
    }

    @Test
    @DisplayName("liberou: nasce um repasse pendente com o valor do repasse; sem conferência da chave, nada sai")
    void semConferenciaNaoSai() {
        Map<String, Object> r = repasse();
        assertThat(r.get("status")).isEqualTo("pendente");
        BigDecimal valorDia =
                jdbc.queryForObject("SELECT valor FROM diaria WHERE id = ?", BigDecimal.class, diarias.get(0));
        assertThat((BigDecimal) r.get("valor")).isEqualByComparingTo(valorDia);

        envio.executar();

        assertThat(repasse().get("status")).isEqualTo("pendente");
        assertThat(gateway.transferenciaPorReferencia(diarias.get(0).toString()))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "chave conferida: o Pix sai com o id da diária; o webhook confirma uma vez e baixa a conta do profissional")
    void conferidaSaiEConfirma(CapturedOutput saida) throws Exception {
        conferirPix().andExpect(status().isNoContent());
        BigDecimal antes = saldoDoProfissional();

        envio.executar();

        Map<String, Object> enviado = repasse();
        assertThat(enviado.get("status")).isEqualTo("enviado");
        String externo = (String) enviado.get("id_externo");
        assertThat(gateway.transferenciaPorReferencia(diarias.get(0).toString()))
                .hasValueSatisfying(t -> assertThat(t.idExterno()).isEqualTo(externo));

        gateway.concluirTransferencia(externo, true, null);
        eventoDeTransferencia("TRANSFER_DONE", externo, null).andExpect(status().isOk());
        eventoDeTransferencia("TRANSFER_DONE", externo, null).andExpect(status().isOk()); // outro evento, mesmo efeito

        Map<String, Object> confirmado = repasse();
        assertThat(confirmado.get("status")).isEqualTo("confirmado");
        assertThat(confirmado.get("transacao_id")).isNotNull();
        assertThat(saldoDoProfissional()).isEqualByComparingTo(antes.subtract((BigDecimal) confirmado.get("valor")));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM transacao_financeira WHERE chave_idempotencia = ?",
                        Integer.class,
                        "repasse:" + diarias.get(0)))
                .isEqualTo(1);
        assertThat(saida.getAll()).doesNotContain(chaveEmClaro());
    }

    @Test
    @DisplayName("caiu depois de mandar: a próxima rodada acha a transferência pela referência e não manda de novo")
    void naoMandaDuasVezes() throws Exception {
        conferirPix().andExpect(status().isNoContent());
        GatewayPagamento.TransferenciaNoGateway jaMandada =
                gateway.transferirPix(new GatewayPagamento.NovaTransferencia(
                        diarias.get(0).toString(),
                        Dinheiro.de("200.00"),
                        GatewayPagamento.TipoChave.EVP,
                        "qualquer",
                        "teste"));

        envio.executar();

        assertThat(repasse().get("id_externo")).isEqualTo(jaMandada.idExterno());
    }

    @Test
    @DisplayName("falhou: alerta no painel e auditoria; o admin manda de novo (auditado) e sai outra transferência")
    void falhaETentaDeNovo() throws Exception {
        conferirPix().andExpect(status().isNoContent());
        envio.executar();
        String primeira = (String) repasse().get("id_externo");
        gateway.concluirTransferencia(primeira, false, "INVALID_PIX_KEY");

        eventoDeTransferencia("TRANSFER_FAILED", primeira, "INVALID_PIX_KEY").andExpect(status().isOk());

        Map<String, Object> falhou = repasse();
        UUID repasse = (UUID) falhou.get("id");
        assertThat(falhou.get("status")).isEqualTo("falhou");
        assertThat(falhou.get("erro")).isEqualTo("chave-invalida");
        mockMvc.perform(get("/api/admin/financeiro/alertas").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.referencia == '" + repasse + "')].tipo")
                        .value("repasse-falhou"));
        mockMvc.perform(get("/api/admin/financeiro/repasses?status=falhou").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.itens[?(@.id == '" + repasse + "')].erro").value("chave-invalida"));

        mockMvc.perform(post("/api/admin/financeiro/repasses/" + repasse + "/tentar-de-novo")
                        .header("Authorization", doAdmin()))
                .andExpect(status().isNoContent());
        assertThat(repasse().get("status")).isEqualTo("pendente");
        envio.executar();

        assertThat(repasse().get("status")).isEqualTo("enviado");
        assertThat(repasse().get("id_externo")).isNotEqualTo(primeira);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'repasse.tentar-de-novo' AND entidade_id = ?",
                        Integer.class,
                        repasse))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("trocar a chave Pix desfaz a conferência (o repasse volta a esperar o admin)")
    void trocarAChaveDesfazAConferencia() throws Exception {
        conferirPix().andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                        "SELECT pix_conferido_em IS NOT NULL FROM profissional WHERE id = ?",
                        Boolean.class,
                        profissional.profissional()))
                .isTrue();

        jdbc.update(
                "UPDATE profissional SET chave_pix_cifrada = ? WHERE id = ?",
                contexto.getBean(CifraDeCampo.class).cifrar(UUID.randomUUID().toString(), Campo.CHAVE_PIX),
                profissional.profissional());

        assertThat(jdbc.queryForObject(
                        "SELECT pix_conferido_em IS NULL AND pix_conferido_por IS NULL FROM profissional WHERE id = ?",
                        Boolean.class,
                        profissional.profissional()))
                .isTrue();
    }

    @Test
    @DisplayName("o admin vê a chave em claro para conferir (auditado); cliente e profissional recebem 403")
    void adminVeAChave() throws Exception {
        String caminho = "/api/admin/profissionais/" + profissional.profissional() + "/pix";
        mockMvc.perform(get(caminho).header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chave").value(chaveEmClaro()))
                .andExpect(jsonPath("$.conferida").value(false));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'profissional.pix-visto' AND entidade_id = ?",
                        Integer.class,
                        profissional.profissional()))
                .isEqualTo(1);
        mockMvc.perform(get(caminho).header("Authorization", doCliente())).andExpect(status().isForbidden());
        mockMvc.perform(get(caminho).header("Authorization", doProfissional())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/financeiro/repasses").header("Authorization", doCliente()))
                .andExpect(status().isForbidden());
    }
}
