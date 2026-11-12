package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;

/**
 * Conciliação diária (PA02, RN34e): o saldo da conta da COE no gateway contra o livro-caixa. O esperado é o que a conta
 * {@code gateway} do livro-caixa diz que está lá (custódia + a repassar + receita + a estornar + a devolver − taxas)
 * menos o que já saiu do gateway e a COE só lança na confirmação (repasses, estornos e devoluções enviados). Diferença
 * = alerta no painel do admin e auditoria. Uma instância por vez (ShedLock), às 6 h de São Paulo.
 */
@Component
public class ConciliacaoDiaria {

    private static final Logger LOG = LoggerFactory.getLogger(ConciliacaoDiaria.class);

    /** Os números da conciliação (só valores, nenhum dado pessoal). */
    public record Resultado(BigDecimal saldoGateway, BigDecimal esperado, BigDecimal diferenca) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final GatewayPagamento gateway;
    private final AlertasFinanceiros alertas;
    private final RegistroDeAuditoria auditoria;

    ConciliacaoDiaria(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transacao,
            GatewayPagamento gateway,
            AlertasFinanceiros alertas,
            RegistroDeAuditoria auditoria) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.gateway = gateway;
        this.alertas = alertas;
        this.auditoria = auditoria;
    }

    @Scheduled(cron = "${coe.pagamento.conciliacao:0 0 6 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "conciliacao-diaria", lockAtMostFor = "PT30M")
    public void executar() {
        Resultado primeira = calcular();
        if (!divergente(primeira.saldoGateway(), primeira.esperado())) {
            LOG.info("Conciliação do gateway sem diferença");
            return;
        }
        // o gateway e o livro-caixa não são lidos no mesmo instante: só vale a diferença que se repete
        Resultado resultado = calcular();
        if (resultado.diferenca().compareTo(primeira.diferenca()) != 0) {
            LOG.info("Conciliação do gateway mudou entre as leituras; fica para a próxima rodada");
            return;
        }
        Map<String, String> detalhe = Map.of(
                "saldoGateway", resultado.saldoGateway().toPlainString(),
                "esperado", resultado.esperado().toPlainString(),
                "diferenca", resultado.diferenca().toPlainString());
        transacao.executeWithoutResult(status -> {
            alertas.abrir(AlertasFinanceiros.Tipo.CONCILIACAO, null, detalhe);
            auditoria.registrarDoSistema(
                    "financeiro.conciliacao-divergente", "alerta_financeiro", null, null, detalhe, null);
        });
        LOG.warn(
                "Conciliação do gateway com diferença de {}",
                resultado.diferenca().toPlainString());
    }

    /** O saldo do gateway, o esperado pelo livro-caixa e a diferença (gateway − esperado). */
    public Resultado calcular() {
        BigDecimal espelho = jdbc.queryForObject(
                "SELECT COALESCE(-SUM(saldo), 0) FROM saldo_conta WHERE tipo = 'gateway'", Map.of(), BigDecimal.class);
        BigDecimal aCaminho = jdbc.queryForObject("""
                SELECT (SELECT COALESCE(SUM(valor), 0) FROM repasse WHERE status = 'enviado')
                     + (SELECT COALESCE(SUM(valor_total), 0) FROM reembolso WHERE status = 'enviado')
                     + (SELECT COALESCE(SUM(valor), 0) FROM devolucao WHERE status = 'enviado')
                """, Map.of(), BigDecimal.class);
        BigDecimal esperado = espelho.subtract(aCaminho).setScale(2);
        BigDecimal saldo = gateway.saldo().valor();
        return new Resultado(saldo, esperado, saldo.subtract(esperado));
    }

    /** Qualquer centavo de diferença é divergência. */
    public static boolean divergente(BigDecimal saldoGateway, BigDecimal esperado) {
        return saldoGateway.compareTo(esperado) != 0;
    }
}
