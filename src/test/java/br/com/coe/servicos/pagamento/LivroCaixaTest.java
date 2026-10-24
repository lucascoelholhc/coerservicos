package br.com.coe.servicos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** DOM-07: o livro-caixa recusa transação desbalanceada antes do banco e é idempotente pela chave. */
class LivroCaixaTest extends IntegracaoTest {

    @Autowired
    private LivroCaixa livro;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcTemplate jdbc;

    private static LivroCaixa.Lancamento debito(LivroCaixa.Conta conta, String valor) {
        return new LivroCaixa.Lancamento(conta, LivroCaixa.Natureza.DEBITO, Dinheiro.de(valor));
    }

    private static LivroCaixa.Lancamento credito(LivroCaixa.Conta conta, String valor) {
        return new LivroCaixa.Lancamento(conta, LivroCaixa.Natureza.CREDITO, Dinheiro.de(valor));
    }

    @Test
    @DisplayName("débitos diferentes dos créditos: recusada antes de chegar ao banco")
    void desbalanceada() {
        String chave = "ajuste:teste:" + UUID.randomUUID();
        assertThatThrownBy(() -> transacao.executeWithoutResult(s -> livro.registrar(new LivroCaixa.Transacao(
                        "ajuste",
                        chave,
                        null,
                        null,
                        null,
                        "teste desbalanceado",
                        List.of(
                                debito(LivroCaixa.Conta.GATEWAY, "10.00"),
                                credito(LivroCaixa.Conta.RECEITA_COE, "9.99"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM transacao_financeira WHERE chave_idempotencia = ?", Integer.class, chave))
                .isZero();
    }

    @Test
    @DisplayName("a mesma chave duas vezes: a segunda não grava nada e avisa")
    void idempotentePelaChave() {
        String chave = "ajuste:teste:" + UUID.randomUUID();
        LivroCaixa.Transacao ajuste = new LivroCaixa.Transacao(
                "ajuste",
                chave,
                null,
                null,
                null,
                "teste de chave",
                List.of(debito(LivroCaixa.Conta.GATEWAY, "1.00"), credito(LivroCaixa.Conta.RECEITA_COE, "1.00")));

        Boolean primeira = transacao.execute(s -> livro.registrar(ajuste));
        Boolean segunda = transacao.execute(s -> livro.registrar(ajuste));

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM lancamento l JOIN transacao_financeira t ON t.id = l.transacao_id
                         WHERE t.chave_idempotencia = ?
                        """, Integer.class, chave)).isEqualTo(2);
    }
}
