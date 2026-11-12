package br.com.coe.servicos.pagamento;

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
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * O dinheiro que volta e as taxas na conta única (PA02). Estorno de uma diária reembolsada (RN34b): D cliente, C
 * gateway ({@code estorno:<diária>}). Dinheiro recebido sem pedido (RN34c): D gateway, C a devolver
 * ({@code sem-pedido:<cobrança>}) e uma linha de {@code devolucao}; ao confirmar, D a devolver, C gateway
 * ({@code devolucao:<cobrança>}). Taxa do gateway (RN34d): D despesa da COE, C gateway, só quando maior que zero; nunca
 * sai do repasse do profissional. Tudo na transação de quem chama, com a linha já travada.
 */
@Component
public class Estornos {

    private final NamedParameterJdbcTemplate jdbc;
    private final LivroCaixa livro;
    private final AlertasFinanceiros alertas;
    private final RegistroDeAuditoria auditoria;
    private final ConfiguracaoNegocio configuracao;

    Estornos(
            NamedParameterJdbcTemplate jdbc,
            LivroCaixa livro,
            AlertasFinanceiros alertas,
            RegistroDeAuditoria auditoria,
            ConfiguracaoNegocio configuracao) {
        this.jdbc = jdbc;
        this.livro = livro;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.configuracao = configuracao;
    }

    /** O gateway devolveu ao cliente o que ele pagou pela diária. */
    @Transactional(propagation = Propagation.MANDATORY)
    void confirmarReembolso(UUID reembolso, OffsetDateTime agora) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT r.diaria_id, r.valor_total, r.cobranca_id, d.contrato_id, d.cliente_id
                  FROM reembolso r JOIN diaria d ON d.id = r.diaria_id
                 WHERE r.id = :id
                """, new MapSqlParameterSource("id", reembolso));
        Dinheiro valor = new Dinheiro((BigDecimal) r.get("valor_total"));
        UUID diaria = (UUID) r.get("diaria_id");
        livro.registrar(new LivroCaixa.Transacao(
                "estorno",
                "estorno:" + diaria,
                (UUID) r.get("contrato_id"),
                diaria,
                (UUID) r.get("cobranca_id"),
                "Estorno da diária ao cliente",
                List.of(
                        LivroCaixa.Lancamento.paraCliente(
                                (UUID) r.get("cliente_id"), LivroCaixa.Natureza.DEBITO, valor),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.CREDITO, valor))));
        jdbc.update(
                "UPDATE reembolso SET status = 'confirmado', concluido_em = :agora, erro = NULL WHERE id = :id",
                new MapSqlParameterSource("id", reembolso).addValue("agora", agora));
        auditoria.registrarDoSistema(
                "reembolso.estornado",
                "reembolso",
                reembolso,
                Map.of("status", "enviado"),
                Map.of("status", "confirmado", "valor", valor.valor().toPlainString()),
                null);
    }

    /** O gateway devolveu inteiro o dinheiro que chegou sem pedido. */
    @Transactional(propagation = Propagation.MANDATORY)
    void confirmarDevolucao(UUID devolucao, OffsetDateTime agora) {
        Map<String, Object> d = jdbc.queryForMap("""
                SELECT dv.cobranca_id, dv.valor, c.contrato_id
                  FROM devolucao dv JOIN cobranca c ON c.id = dv.cobranca_id
                 WHERE dv.id = :id
                """, new MapSqlParameterSource("id", devolucao));
        Dinheiro valor = new Dinheiro((BigDecimal) d.get("valor"));
        UUID cobranca = (UUID) d.get("cobranca_id");
        livro.registrar(new LivroCaixa.Transacao(
                "devolucao",
                "devolucao:" + cobranca,
                (UUID) d.get("contrato_id"),
                null,
                cobranca,
                "Devolução do dinheiro recebido sem pedido",
                List.of(
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.A_DEVOLVER, LivroCaixa.Natureza.DEBITO, valor),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.CREDITO, valor))));
        jdbc.update(
                "UPDATE devolucao SET status = 'confirmado', confirmado_em = :agora, erro = NULL WHERE id = :id",
                new MapSqlParameterSource("id", devolucao).addValue("agora", agora));
        auditoria.registrarDoSistema(
                "devolucao.confirmada",
                "devolucao",
                devolucao,
                Map.of("status", "enviado"),
                Map.of("status", "confirmado", "valor", valor.valor().toPlainString()),
                null);
    }

    /** Estorno ou devolução recusado pelo gateway: falhou, alerta no painel e auditoria (a equipe resolve). */
    @Transactional(propagation = Propagation.MANDATORY)
    void falhar(Destino destino, UUID id, String motivo) {
        jdbc.update(
                "UPDATE " + destino.tabela + " SET status = 'falhou', erro = :erro WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("erro", motivo));
        alertas.abrir(destino.alerta, id, Map.of("motivo", motivo));
        auditoria.registrarDoSistema(
                destino.tabela + ".falhou",
                destino.tabela,
                id,
                Map.of("status", "enviado"),
                Map.of("motivo", motivo),
                null);
    }

    /**
     * Dinheiro recebido sem pedido esperando (RN34c): vai para "a devolver" e nasce a devolução integral (o pedido
     * nunca é reativado). O mesmo dinheiro avisado de novo pelo gateway não lança duas vezes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void registrarSemPedido(UUID contrato, UUID cobranca, Dinheiro valor, String motivo) {
        boolean novo = livro.registrar(new LivroCaixa.Transacao(
                "sem_pedido",
                "sem-pedido:" + cobranca,
                contrato,
                null,
                cobranca,
                "Dinheiro recebido sem pedido, a devolver",
                List.of(
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.DEBITO, valor),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.A_DEVOLVER, LivroCaixa.Natureza.CREDITO, valor))));
        if (!novo) {
            return;
        }
        UUID devolucao = jdbc.queryForObject(
                """
                INSERT INTO devolucao (cobranca_id, transacao_id, valor, motivo)
                SELECT :cobranca, id, :valor, :motivo FROM transacao_financeira WHERE chave_idempotencia = :chave
                RETURNING id
                """,
                new MapSqlParameterSource("cobranca", cobranca)
                        .addValue("valor", valor.valor())
                        .addValue("motivo", motivo)
                        .addValue("chave", "sem-pedido:" + cobranca),
                UUID.class);
        alertas.abrir(
                AlertasFinanceiros.Tipo.DINHEIRO_SEM_PEDIDO,
                devolucao,
                Map.of("motivo", motivo, "valor", valor.valor().toPlainString()));
    }

    /** Taxa do gateway sobre o dinheiro recebido (Pix ou cartão), quando maior que zero. */
    @Transactional(propagation = Propagation.MANDATORY)
    void taxaDoRecebimento(UUID contrato, UUID cobranca, GatewayPagamento.Metodo metodo, Dinheiro recebido) {
        ConfiguracaoNegocio.TaxasDoGateway taxas = configuracao.taxasDoGateway();
        Dinheiro taxa = metodo == GatewayPagamento.Metodo.PIX
                ? taxas.pix()
                : recebido.multiplicar(taxas.cartaoPercentual()).somar(taxas.cartaoFixa());
        lancarTaxa("taxa:" + cobranca, contrato, null, cobranca, taxa, "Taxa do gateway no recebimento");
    }

    /** Taxa do gateway sobre a transferência do repasse, quando maior que zero (nunca sai do profissional). */
    @Transactional(propagation = Propagation.MANDATORY)
    void taxaDaTransferencia(UUID contrato, UUID diaria) {
        lancarTaxa(
                "taxa-repasse:" + diaria,
                contrato,
                diaria,
                null,
                configuracao.taxasDoGateway().transferencia(),
                "Taxa do gateway na transferência do repasse");
    }

    private void lancarTaxa(String chave, UUID contrato, UUID diaria, UUID cobranca, Dinheiro taxa, String descricao) {
        if (taxa.valor().signum() == 0) {
            return;
        }
        livro.registrar(new LivroCaixa.Transacao(
                "taxa",
                chave,
                contrato,
                diaria,
                cobranca,
                descricao,
                List.of(
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.DESPESA_COE, LivroCaixa.Natureza.DEBITO, taxa),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.CREDITO, taxa))));
    }

    /** Para onde vai o dinheiro de volta: a diária reembolsada ou a devolução integral. */
    enum Destino {
        REEMBOLSO("reembolso", AlertasFinanceiros.Tipo.ESTORNO_FALHOU),
        DEVOLUCAO("devolucao", AlertasFinanceiros.Tipo.DEVOLUCAO_FALHOU);

        final String tabela;
        final AlertasFinanceiros.Tipo alerta;

        Destino(String tabela, AlertasFinanceiros.Tipo alerta) {
            this.tabela = tabela;
            this.alerta = alerta;
        }
    }
}
