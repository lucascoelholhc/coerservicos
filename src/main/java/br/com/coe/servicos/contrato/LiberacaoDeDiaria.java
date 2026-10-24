package br.com.coe.servicos.contrato;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Libera uma diária (RN37–RN39): status {@code liberada}, histórico, livro-caixa (D custódia o que o cliente pagou
 * pelo dia; C profissional o repasse; C receita a comissão, com a comissão e a taxa congeladas no contrato) e
 * auditoria, tudo na transação de quem chama, que já travou o contrato e a diária (nessa ordem) e conferiu o status.
 * Só usa o dinheiro guardado daquele contrato: sem custódia suficiente, nada muda. A chave {@code liberacao:<diaria>}
 * e o {@code uq_transacao_diaria_destino} garantem uma liberação só.
 */
@Component
class LiberacaoDeDiaria {

    /** Quem liberou: o valor de {@code diaria.liberada_por} e a origem no histórico. */
    enum Por {
        CLIENTE("cliente", "cliente"),
        AUTOMATICA("automatica", "sistema");

        private final String codigo;
        private final String origem;

        Por(String codigo, String origem) {
            this.codigo = codigo;
            this.origem = origem;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LivroCaixa livro;
    private final RegistroDeAuditoria auditoria;

    LiberacaoDeDiaria(NamedParameterJdbcTemplate jdbc, LivroCaixa livro, RegistroDeAuditoria auditoria) {
        this.jdbc = jdbc;
        this.livro = livro;
        this.auditoria = auditoria;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void liberar(UUID contrato, UUID diaria, Por por, UUID ator, OffsetDateTime agora) {
        Map<String, Object> c = jdbc.queryForMap(
                "SELECT numero, comissao_pct, taxa_paga_por FROM contrato WHERE id = :id",
                new MapSqlParameterSource("id", contrato));
        Map<String, Object> d = jdbc.queryForMap(
                "SELECT valor, profissional_id FROM diaria WHERE id = :id", new MapSqlParameterSource("id", diaria));
        ValoresDiaria valores = CalculadoraDiaria.calcular(
                new Dinheiro((BigDecimal) d.get("valor")),
                new Percentual((BigDecimal) c.get("comissao_pct")),
                TaxaPagaPor.deCodigo((String) c.get("taxa_paga_por")));
        Dinheiro guardado = livro.saldoDeCustodia(contrato);
        if (guardado.valor().compareTo(valores.totalCliente().valor()) < 0) {
            // estado impossível pelo fluxo (diária aguardando sem pagamento): nunca usa dinheiro de outro contrato
            throw new IllegalStateException("Custódia insuficiente para liberar a diária " + diaria);
        }

        int liberadas = jdbc.update(
                """
                UPDATE diaria SET status = 'liberada', liberada_em = :agora, liberada_por = :por,
                                  aprovada_em = CASE WHEN :por = 'cliente' THEN :agora ELSE aprovada_em END
                 WHERE id = :id AND status = 'aguardando'
                """,
                new MapSqlParameterSource("id", diaria).addValue("agora", agora).addValue("por", por.codigo));
        if (liberadas != 1) {
            // quem chama confere o status dentro da trava; chegar aqui é erro de programação, não do cliente
            throw new IllegalStateException("Diária fora de aguardando na liberação: " + diaria);
        }
        jdbc.update(
                """
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, ator_id, criado_em)
                VALUES (:diaria, 'aguardando', 'liberada', :origem, :ator, :agora)
                """,
                new MapSqlParameterSource("diaria", diaria)
                        .addValue("origem", por.origem)
                        .addValue("ator", ator)
                        .addValue("agora", agora));

        List<LivroCaixa.Lancamento> lancamentos = new ArrayList<>();
        lancamentos.add(new LivroCaixa.Lancamento(
                LivroCaixa.Conta.CUSTODIA, LivroCaixa.Natureza.DEBITO, valores.totalCliente()));
        lancamentos.add(LivroCaixa.Lancamento.paraProfissional(
                (UUID) d.get("profissional_id"), LivroCaixa.Natureza.CREDITO, valores.repasseProfissional()));
        if (valores.comissao().valor().signum() > 0) {
            lancamentos.add(new LivroCaixa.Lancamento(
                    LivroCaixa.Conta.RECEITA_COE, LivroCaixa.Natureza.CREDITO, valores.comissao()));
        }
        boolean registrada = livro.registrar(new LivroCaixa.Transacao(
                "liberacao",
                "liberacao:" + diaria,
                contrato,
                diaria,
                null,
                "Liberação da diária do pedido nº " + ((Number) c.get("numero")).longValue(),
                lancamentos));
        if (!registrada) {
            throw new IllegalStateException("Diária já liberada no livro-caixa: " + diaria);
        }

        Map<String, Object> depois = Map.of(
                "status",
                "liberada",
                "por",
                por.codigo,
                "repasse",
                valores.repasseProfissional().valor().toPlainString(),
                "comissao",
                valores.comissao().valor().toPlainString());
        if (por == Por.CLIENTE) {
            auditoria.registrar(
                    ator, "CLIENTE", "diaria.liberada", "diaria", diaria, Map.of("status", "aguardando"), depois, null);
        } else {
            auditoria.registrarDoSistema(
                    "diaria.liberada", "diaria", diaria, Map.of("status", "aguardando"), depois, null);
        }
    }
}
