package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Livro-caixa de partidas dobradas (RN33, V6): cada {@link Transacao} tem débitos = créditos, conferido aqui antes do
 * banco e de novo pelo banco no COMMIT. Nada é alterado nem apagado; correção = nova transação. A chave de
 * idempotência (ex.: {@code pagamento:<cobranca>}, {@code liberacao:<diaria>}) impede registrar o mesmo fato duas
 * vezes. A custódia é uma conta só, rastreada por contrato pelas transações dele ({@link #saldoDeCustodia}).
 */
@Component
public class LivroCaixa {

    /**
     * Contas do livro-caixa: as da COE (sem dono), a de cada profissional (dono = id do perfil) e a de cada cliente
     * (dono = id do usuário; recebe o reembolso, DOM-09). As com dono nascem no primeiro lançamento.
     */
    public enum Conta {
        GATEWAY("gateway"),
        CUSTODIA("custodia"),
        RECEITA_COE("receita_coe"),
        PROFISSIONAL("profissional"),
        CLIENTE("cliente");

        private final String tipo;

        Conta(String tipo) {
            this.tipo = tipo;
        }
    }

    public enum Natureza {
        DEBITO("D"),
        CREDITO("C");

        private final String letra;

        Natureza(String letra) {
            this.letra = letra;
        }
    }

    /**
     * {@code dono}: o id do perfil na conta PROFISSIONAL, o id do usuário na conta CLIENTE; nulo nas contas da COE.
     */
    public record Lancamento(Conta conta, Natureza natureza, Dinheiro valor, UUID dono) {

        public Lancamento {
            boolean temDono = conta == Conta.PROFISSIONAL || conta == Conta.CLIENTE;
            if (temDono != (dono != null)) {
                throw new IllegalArgumentException("Só as contas do profissional e do cliente têm dono: " + conta);
            }
        }

        public Lancamento(Conta conta, Natureza natureza, Dinheiro valor) {
            this(conta, natureza, valor, null);
        }

        public static Lancamento paraProfissional(UUID profissional, Natureza natureza, Dinheiro valor) {
            return new Lancamento(Conta.PROFISSIONAL, natureza, valor, profissional);
        }

        /** A conta do cliente (usuário): recebe o reembolso até o estorno no gateway. */
        public static Lancamento paraCliente(UUID usuario, Natureza natureza, Dinheiro valor) {
            return new Lancamento(Conta.CLIENTE, natureza, valor, usuario);
        }
    }

    public record Transacao(
            String tipo,
            String chave,
            UUID contrato,
            UUID diaria,
            UUID cobranca,
            String descricao,
            List<Lancamento> lancamentos) {

        public Transacao {
            lancamentos = List.copyOf(lancamentos);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    LivroCaixa(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava a transação e os lançamentos na transação do banco já aberta (o fato e o dinheiro juntos). Devolve
     * {@code false} sem gravar nada se a chave já foi usada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrar(Transacao transacao) {
        exigirBalanceada(transacao.lancamentos());
        List<UUID> criada = jdbc.queryForList(
                """
                INSERT INTO transacao_financeira (tipo, chave_idempotencia, contrato_id, diaria_id, cobranca_id, descricao)
                VALUES (:tipo, :chave, :contrato, :diaria, :cobranca, :descricao)
                ON CONFLICT (chave_idempotencia) DO NOTHING
                RETURNING id
                """,
                new MapSqlParameterSource("tipo", transacao.tipo())
                        .addValue("chave", transacao.chave())
                        .addValue("contrato", transacao.contrato())
                        .addValue("diaria", transacao.diaria())
                        .addValue("cobranca", transacao.cobranca())
                        .addValue("descricao", transacao.descricao()),
                UUID.class);
        if (criada.isEmpty()) {
            return false;
        }
        for (Lancamento lancamento : transacao.lancamentos()) {
            if (lancamento.conta() == Conta.PROFISSIONAL) {
                // a conta do profissional nasce no primeiro lançamento dele (uq_conta_razao segura a corrida)
                jdbc.update("""
                        INSERT INTO conta_razao (tipo, profissional_id, nome)
                        VALUES ('profissional', :dono, 'Valores liberados ao profissional')
                        ON CONFLICT ON CONSTRAINT uq_conta_razao DO NOTHING
                        """, new MapSqlParameterSource("dono", lancamento.dono()));
            } else if (lancamento.conta() == Conta.CLIENTE) {
                jdbc.update("""
                        INSERT INTO conta_razao (tipo, usuario_id, nome)
                        VALUES ('cliente', :dono, 'Reembolsos ao cliente')
                        ON CONFLICT ON CONSTRAINT uq_conta_razao DO NOTHING
                        """, new MapSqlParameterSource("dono", lancamento.dono()));
            }
            // com dono, o uq_conta_razao (tipo, profissional_id, usuario_id) serve de índice; sem dono, as da COE
            String conta = switch (lancamento.conta()) {
                case PROFISSIONAL -> "tipo = :conta AND profissional_id = :dono AND usuario_id IS NULL";
                case CLIENTE -> "tipo = :conta AND usuario_id = :dono AND profissional_id IS NULL";
                default -> "tipo = :conta AND profissional_id IS NULL AND usuario_id IS NULL";
            };
            int gravados = jdbc.update(
                    "INSERT INTO lancamento (transacao_id, conta_id, natureza, valor)"
                            + " SELECT :transacao, id, :natureza, :valor FROM conta_razao WHERE " + conta,
                    new MapSqlParameterSource("transacao", criada.get(0))
                            .addValue("natureza", lancamento.natureza().letra)
                            .addValue("valor", lancamento.valor().valor())
                            .addValue("conta", lancamento.conta().tipo)
                            .addValue("dono", lancamento.dono()));
            if (gravados != 1) {
                throw new IllegalStateException("Conta do livro-caixa não encontrada: " + lancamento.conta());
            }
        }
        return true;
    }

    /** O dinheiro do contrato que ainda está guardado (créditos − débitos da custódia nas transações dele). */
    public Dinheiro saldoDeCustodia(UUID contrato) {
        BigDecimal saldo = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE l.natureza WHEN 'C' THEN l.valor ELSE -l.valor END), 0)
                  FROM lancamento l
                  JOIN transacao_financeira t ON t.id = l.transacao_id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.contrato_id = :contrato AND r.tipo = 'custodia'
                """, new MapSqlParameterSource("contrato", contrato), BigDecimal.class);
        if (saldo.signum() < 0) {
            throw new IllegalStateException("Custódia negativa no contrato " + contrato);
        }
        return new Dinheiro(saldo.setScale(2));
    }

    private static void exigirBalanceada(List<Lancamento> lancamentos) {
        if (lancamentos.isEmpty()) {
            throw new IllegalArgumentException("Transação sem lançamentos.");
        }
        BigDecimal debitos = BigDecimal.ZERO;
        BigDecimal creditos = BigDecimal.ZERO;
        for (Lancamento lancamento : lancamentos) {
            if (lancamento.natureza() == Natureza.DEBITO) {
                debitos = debitos.add(lancamento.valor().valor());
            } else {
                creditos = creditos.add(lancamento.valor().valor());
            }
        }
        if (debitos.compareTo(creditos) != 0) {
            throw new IllegalArgumentException(
                    "Transação desbalanceada: débitos " + debitos + " ≠ créditos " + creditos);
        }
    }
}
