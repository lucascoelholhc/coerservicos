package br.com.coe.servicos.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta, via SQL puro, o mínimo de dados para exercitar as constraints do schema.
 * Celular, código IBGE, CPF e ids externos vêm de um contador: únicos e determinísticos em toda a execução.
 */
class FixturesBanco {

    private static final AtomicLong SEQUENCIA = new AtomicLong();

    static final String TAXA_CLIENTE = "cliente";
    static final String TAXA_PROFISSIONAL = "profissional";
    static final LocalDate NASCIMENTO_ADULTO = LocalDate.of(1990, 5, 20);
    static final int RAIO_PADRAO_KM = 10;

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

    /** Profissional em rascunho (cadastro incompleto). */
    UUID profissional() {
        return profissionalDoUsuario(usuario());
    }

    UUID profissionalDoUsuario(UUID usuarioId) {
        return jdbc.queryForObject(
                "INSERT INTO profissional (usuario_id) VALUES (?) RETURNING id", UUID.class, usuarioId);
    }

    /** Profissional com cadastro completo, enviado para análise. */
    UUID profissionalEmAnalise() {
        return profissionalEmAnalise(NASCIMENTO_ADULTO, RAIO_PADRAO_KM);
    }

    UUID profissionalEmAnalise(LocalDate dataNascimento, int raioKm) {
        String cpf = String.format("%011d", SEQUENCIA.incrementAndGet());
        return jdbc.queryForObject("""
                INSERT INTO profissional (usuario_id, status, profissao_principal_id, experiencia_anos,
                                          valor_diaria, cidade_base_id, raio_km, cpf_cifrado, cpf_hash,
                                          chave_pix_tipo, chave_pix_cifrada, data_nascimento, enviado_analise_em)
                VALUES (?, 'em_analise', (SELECT id FROM profissao WHERE codigo = 'pedreiro'), 5,
                        280.00, ?, ?, '\\x01'::bytea, sha256(convert_to(?, 'UTF8')),
                        'cpf', '\\x02'::bytea, ?, now())
                RETURNING id
                """, UUID.class, usuario(), cidade(), raioKm, cpf, dataNascimento);
    }

    /** Contrato válido de uma diária de R$ 280 com 10% de comissão paga pelo cliente. */
    UUID contrato(UUID clienteId, UUID profissionalId) {
        return contrato(clienteId, profissionalId, TAXA_CLIENTE,
                new BigDecimal("280.00"), new BigDecimal("28.00"), new BigDecimal("308.00"));
    }

    UUID contrato(UUID clienteId, UUID profissionalId, String taxaPagaPor,
                  BigDecimal valorServicos, BigDecimal valorComissao, BigDecimal valorTotal) {
        return contrato(clienteId, profissionalId, taxaPagaPor, valorServicos, valorComissao, valorTotal,
                "Reboco da parede da sala");
    }

    UUID contratoComDescricao(String descricao) {
        return contrato(usuario(), profissional(), TAXA_CLIENTE,
                new BigDecimal("280.00"), new BigDecimal("28.00"), new BigDecimal("308.00"), descricao);
    }

    private UUID contrato(UUID clienteId, UUID profissionalId, String taxaPagaPor, BigDecimal valorServicos,
                          BigDecimal valorComissao, BigDecimal valorTotal, String descricao) {
        return jdbc.queryForObject("""
                INSERT INTO contrato (cliente_id, profissional_id, profissao_id, descricao, material, cep,
                                      cidade_id, endereco_cifrado, valor_diaria, comissao_pct, taxa_paga_por,
                                      valor_servicos, valor_comissao, valor_total, versao_termos,
                                      pagamento_expira_em)
                VALUES (?, ?, (SELECT id FROM profissao WHERE codigo = 'pedreiro'),
                        ?, 'combinar', '89010000', ?, '\\x00'::bytea,
                        280.00, 0.1000, ?, ?, ?, ?, '1.0', now() + interval '1 hour')
                RETURNING id
                """, UUID.class, clienteId, profissionalId, descricao, cidade(), taxaPagaPor,
                valorServicos, valorComissao, valorTotal);
    }

    UUID diaria(UUID contratoId, UUID clienteId, UUID profissionalId, LocalDate data) {
        return jdbc.queryForObject("""
                INSERT INTO diaria (contrato_id, cliente_id, profissional_id, data, valor, valor_comissao)
                VALUES (?, ?, ?, ?, 280.00, 28.00) RETURNING id
                """, UUID.class, contratoId, clienteId, profissionalId, data);
    }

    /** Diária agendada num contrato novo, com cliente e profissional novos. */
    UUID diariaAvulsa(LocalDate data) {
        UUID cliente = usuario();
        UUID profissional = profissional();
        return diaria(contrato(cliente, profissional), cliente, profissional, data);
    }

    void cancelarDiaria(UUID diariaId) {
        jdbc.update("UPDATE diaria SET status = 'cancelada', cancelada_em = now() WHERE id = ?", diariaId);
    }

    UUID cobranca(UUID contratoId) {
        return jdbc.queryForObject("""
                INSERT INTO cobranca (contrato_id, gateway, metodo, valor)
                VALUES (?, 'falso', 'pix', 308.00) RETURNING id
                """, UUID.class, contratoId);
    }

    /** Transação de ajuste, sem vínculo com diária ou cobrança (serve aos testes do ledger). */
    UUID transacaoFinanceira() {
        return transacaoFinanceira("teste:" + UUID.randomUUID());
    }

    UUID transacaoFinanceira(String chaveIdempotencia) {
        return transacaoFinanceira("ajuste", chaveIdempotencia, null, null);
    }

    UUID transacaoFinanceira(String tipo, String chaveIdempotencia, UUID diariaId, UUID cobrancaId) {
        return jdbc.queryForObject("""
                INSERT INTO transacao_financeira (tipo, chave_idempotencia, diaria_id, cobranca_id, descricao)
                VALUES (?, ?, ?, ?, 'Transação de teste') RETURNING id
                """, UUID.class, tipo, chaveIdempotencia, diariaId, cobrancaId);
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
        eventoGateway(gateway, idEvento, true);
    }

    UUID eventoGateway(String gateway, String idEvento, boolean assinaturaValida) {
        return jdbc.queryForObject("""
                INSERT INTO evento_gateway (gateway, id_evento, tipo, payload, corpo_bruto, assinatura_valida)
                VALUES (?, ?, 'cobranca.confirmada', '{}'::jsonb, convert_to('{}', 'UTF8'), ?)
                RETURNING id
                """, UUID.class, gateway, idEvento, assinaturaValida);
    }

    UUID documentoPendente(UUID profissionalId) {
        return jdbc.queryForObject("""
                INSERT INTO documento_verificacao (profissional_id, tipo, chave_arquivo)
                VALUES (?, 'selfie', ?) RETURNING id
                """, UUID.class, profissionalId, "docs/" + UUID.randomUUID());
    }

    String idExterno() {
        return "ext_" + SEQUENCIA.incrementAndGet();
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
