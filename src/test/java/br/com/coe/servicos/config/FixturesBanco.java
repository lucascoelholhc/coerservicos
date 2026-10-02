package br.com.coe.servicos.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta, via SQL puro, o mínimo de dados para exercitar as constraints do schema.
 * Celular e código IBGE vêm de um contador: únicos e determinísticos em toda a execução.
 */
class FixturesBanco {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    static final String TAXA_CLIENTE = "cliente";
    static final String TAXA_PROFISSIONAL = "profissional";

    private final JdbcTemplate jdbc;

    FixturesBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    UUID usuario() {
        String celular = String.valueOf(47_900_000_000L + SEQUENCIA.incrementAndGet());
        return jdbc.queryForObject("""
                INSERT INTO usuario (nome, celular, senha_hash)
                VALUES ('Usuário de teste', ?, 'hash-de-teste') RETURNING id
                """, UUID.class, celular);
    }

    UUID cidade() {
        long codigoIbge = 1_000_000L + SEQUENCIA.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO cidade (codigo_ibge, nome, uf, latitude, longitude, ativa)
                VALUES (?, 'Cidade de teste', 'SC', -26.9194, -49.0661, true)
                RETURNING id
                """, UUID.class, codigoIbge);
    }

    UUID profissional() {
        return jdbc.queryForObject(
                "INSERT INTO profissional (usuario_id) VALUES (?) RETURNING id", UUID.class, usuario());
    }

    /** Contrato válido de uma diária de R$ 280 com 10% de comissão paga pelo cliente. */
    UUID contrato(UUID clienteId, UUID profissionalId) {
        return contrato(clienteId, profissionalId, TAXA_CLIENTE,
                new BigDecimal("280.00"), new BigDecimal("28.00"), new BigDecimal("308.00"));
    }

    UUID contrato(UUID clienteId, UUID profissionalId, String taxaPagaPor,
                  BigDecimal valorServicos, BigDecimal valorComissao, BigDecimal valorTotal) {
        return jdbc.queryForObject("""
                INSERT INTO contrato (cliente_id, profissional_id, profissao_id, descricao, material, cep,
                                      cidade_id, endereco_cifrado, valor_diaria, comissao_pct, taxa_paga_por,
                                      valor_servicos, valor_comissao, valor_total, versao_termos,
                                      pagamento_expira_em)
                VALUES (?, ?, (SELECT id FROM profissao WHERE codigo = 'pedreiro'),
                        'Reboco da parede da sala', 'combinar', '89010000', ?, '\\x00'::bytea,
                        280.00, 0.1000, ?, ?, ?, ?, '1.0', now() + interval '1 hour')
                RETURNING id
                """, UUID.class, clienteId, profissionalId, cidade(), taxaPagaPor,
                valorServicos, valorComissao, valorTotal);
    }

    UUID diaria(UUID contratoId, UUID clienteId, UUID profissionalId, LocalDate data) {
        return jdbc.queryForObject("""
                INSERT INTO diaria (contrato_id, cliente_id, profissional_id, data, valor, valor_comissao)
                VALUES (?, ?, ?, ?, 280.00, 28.00) RETURNING id
                """, UUID.class, contratoId, clienteId, profissionalId, data);
    }

    void cancelarDiaria(UUID diariaId) {
        jdbc.update("UPDATE diaria SET status = 'cancelada', cancelada_em = now() WHERE id = ?", diariaId);
    }

    UUID transacaoFinanceira() {
        return transacaoFinanceira("teste:" + UUID.randomUUID());
    }

    UUID transacaoFinanceira(String chaveIdempotencia) {
        return jdbc.queryForObject("""
                INSERT INTO transacao_financeira (tipo, chave_idempotencia, descricao)
                VALUES ('pagamento', ?, 'Transação de teste') RETURNING id
                """, UUID.class, chaveIdempotencia);
    }

    /** natureza 'D' ou 'C'; tipoConta é uma conta do sistema: gateway, custodia ou receita_coe. */
    long lancamento(UUID transacaoId, String tipoConta, String natureza, String valor) {
        return jdbc.queryForObject("""
                INSERT INTO lancamento (transacao_id, conta_id, natureza, valor)
                VALUES (?, (SELECT id FROM conta_razao
                            WHERE tipo = ? AND profissional_id IS NULL AND usuario_id IS NULL), ?, ?)
                RETURNING id
                """, Long.class, transacaoId, tipoConta, natureza, new BigDecimal(valor));
    }

    void eventoGateway(String gateway, String idEvento) {
        jdbc.update("""
                INSERT INTO evento_gateway (gateway, id_evento, tipo, payload, assinatura_valida)
                VALUES (?, ?, 'cobranca.confirmada', '{}'::jsonb, true)
                """, gateway, idEvento);
    }

    /** Força a checagem adiada (DEFERRABLE) das partidas dobradas sem precisar de COMMIT. */
    void conferirPartidasAgora() {
        jdbc.execute("SET CONSTRAINTS trg_transacao_partidas IMMEDIATE");
    }

    /** Daqui até o fim da transação, os comandos rodam com os privilégios do papel da aplicação. */
    void agirComoAplicacao() {
        jdbc.execute("SET LOCAL ROLE coe_app");
    }
}
