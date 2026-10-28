package br.com.coe.servicos.contrato;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.compartilhado.dinheiro.ValoresDiaria;
import br.com.coe.servicos.pagamento.LivroCaixa;

/**
 * Reembolso de uma diária (PA06, RN44, RN44a/b): devolve exatamente o que o cliente pagou por ela (diária + comissão se
 * a taxa é do cliente; só a diária se é do profissional), pelo livro-caixa (D custódia, C cliente) e numa linha de
 * {@code reembolso} pendente. O estorno no gateway fica para o adaptador real (AWS-09). Quem chama já travou o
 * contrato e a diária, nessa ordem, e conferiu o status. A chave {@code reembolso:<diaria>} e o
 * {@code uq_transacao_diaria_destino} garantem um reembolso só, e nunca liberação e reembolso juntos.
 */
@Component
public class ReembolsoDeDiaria {

    /** Por que o dinheiro volta (coluna {@code reembolso.motivo}). */
    public enum Motivo {
        DISPUTA("disputa"),
        FALTA_PROFISSIONAL("falta_profissional");

        private final String codigo;

        Motivo(String codigo) {
            this.codigo = codigo;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LivroCaixa livro;
    private final RegistroDeAuditoria auditoria;

    ReembolsoDeDiaria(NamedParameterJdbcTemplate jdbc, LivroCaixa livro, RegistroDeAuditoria auditoria) {
        this.jdbc = jdbc;
        this.livro = livro;
        this.auditoria = auditoria;
    }

    /**
     * @param de o status em que a diária está (contestada na decisão do admin; paga quando o cliente cancela as outras
     *     diárias depois de uma falta)
     * @param ator quem decidiu (admin ou cliente); {@code papel} vai para o histórico e a auditoria
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Dinheiro reembolsar(
            UUID contrato, UUID diaria, StatusDiaria de, Motivo motivo, UUID ator, String papel, OffsetDateTime agora) {
        de.exigirIrPara(StatusDiaria.REEMBOLSADA);
        Map<String, Object> c = jdbc.queryForMap(
                "SELECT numero, comissao_pct, taxa_paga_por, cliente_id FROM contrato WHERE id = :id",
                new MapSqlParameterSource("id", contrato));
        BigDecimal valor = jdbc.queryForObject(
                "SELECT valor FROM diaria WHERE id = :id", new MapSqlParameterSource("id", diaria), BigDecimal.class);
        TaxaPagaPor taxa = TaxaPagaPor.deCodigo((String) c.get("taxa_paga_por"));
        ValoresDiaria valores = CalculadoraDiaria.calcular(
                new Dinheiro(valor), new Percentual((BigDecimal) c.get("comissao_pct")), taxa);
        Dinheiro devolver = valores.valorReembolso();
        Dinheiro comissaoDevolvida = taxa == TaxaPagaPor.CLIENTE ? valores.comissao() : Dinheiro.de("0.00");
        if (livro.saldoDeCustodia(contrato).valor().compareTo(devolver.valor()) < 0) {
            // estado impossível pelo fluxo (diária paga sem custódia): nunca usa dinheiro de outro contrato
            throw new IllegalStateException("Custódia insuficiente para reembolsar a diária " + diaria);
        }

        int mudou = jdbc.update(
                """
                UPDATE diaria SET status = 'reembolsada', reembolsada_em = :agora
                 WHERE id = :id AND status = :de
                """,
                new MapSqlParameterSource("id", diaria).addValue("agora", agora).addValue("de", de.codigo()));
        if (mudou != 1) {
            throw new IllegalStateException("Diária fora de " + de.codigo() + " no reembolso: " + diaria);
        }
        jdbc.update(
                """
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, ator_id, criado_em)
                VALUES (:diaria, :de, 'reembolsada', :origem, :ator, :agora)
                """,
                new MapSqlParameterSource("diaria", diaria)
                        .addValue("de", de.codigo())
                        .addValue("origem", "ADMIN".equals(papel) ? "admin" : "cliente")
                        .addValue("ator", ator)
                        .addValue("agora", agora));

        UUID cliente = (UUID) c.get("cliente_id");
        boolean registrada = livro.registrar(new LivroCaixa.Transacao(
                "reembolso",
                "reembolso:" + diaria,
                contrato,
                diaria,
                null,
                "Reembolso da diária do pedido nº " + ((Number) c.get("numero")).longValue(),
                List.of(
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.CUSTODIA, LivroCaixa.Natureza.DEBITO, devolver),
                        LivroCaixa.Lancamento.paraCliente(cliente, LivroCaixa.Natureza.CREDITO, devolver))));
        if (!registrada) {
            throw new IllegalStateException("Diária já reembolsada no livro-caixa: " + diaria);
        }
        UUID transacao = jdbc.queryForObject(
                "SELECT id FROM transacao_financeira WHERE chave_idempotencia = :chave",
                new MapSqlParameterSource("chave", "reembolso:" + diaria),
                UUID.class);
        UUID cobranca = jdbc.queryForObject("""
                SELECT id FROM cobranca WHERE contrato_id = :contrato AND status = 'confirmada'
                 ORDER BY confirmada_em DESC, id LIMIT 1
                """, new MapSqlParameterSource("contrato", contrato), UUID.class);
        jdbc.update(
                """
                INSERT INTO reembolso (diaria_id, cobranca_id, transacao_id, motivo, valor_diaria, valor_comissao,
                                       valor_total, status, solicitado_por, criado_em)
                VALUES (:diaria, :cobranca, :transacao, :motivo, :valorDiaria, :valorComissao, :total, 'pendente',
                        :ator, :agora)
                """,
                new MapSqlParameterSource("diaria", diaria)
                        .addValue("cobranca", cobranca)
                        .addValue("transacao", transacao)
                        .addValue("motivo", motivo.codigo)
                        .addValue(
                                "valorDiaria",
                                devolver.subtrair(comissaoDevolvida).valor())
                        .addValue("valorComissao", comissaoDevolvida.valor())
                        .addValue("total", devolver.valor())
                        .addValue("ator", ator)
                        .addValue("agora", agora));
        auditoria.registrar(
                ator,
                papel,
                "diaria.reembolsada",
                "diaria",
                diaria,
                Map.of("status", de.codigo()),
                Map.of(
                        "status",
                        "reembolsada",
                        "motivo",
                        motivo.codigo,
                        "valor",
                        devolver.valor().toPlainString()),
                null);
        return devolver;
    }
}
