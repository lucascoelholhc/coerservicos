package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.compartilhado.dinheiro.ValoresDiaria;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Financeiro do admin (DOM-11 parte 2b, RF55): tudo sai do livro-caixa. Os saldos são os da view {@code saldo_conta}
 * (guardado = custódia, a repassar = contas dos profissionais, receita = comissão das diárias liberadas, a estornar =
 * contas dos clientes); o dinheiro no gateway é o espelho da soma deles. O período soma o que entrou e saiu entre
 * {@code de} e {@code ate} (dias no fuso de negócio), pelo horário em que a transação foi gravada. Nenhum dado pessoal.
 */
@Service
class AdminFinanceiroService {

    private static final int DIAS_PADRAO = 30;
    private static final int DIAS_MAXIMOS = 366;
    private static final int POR_PAGINA = 50;
    /** Teto da página (50 mil diárias): página maior volta a última permitida, sem OFFSET gigante. */
    private static final int PAGINA_MAXIMA = 1_000;

    private static final Set<String> GUARDADAS = Set.of("paga", "andamento", "aguardando");
    private static final Dinheiro NADA = Dinheiro.de("0.00");

    record Saldos(
            Dinheiro guardado,
            Dinheiro aRepassar,
            Dinheiro receita,
            Dinheiro aEstornar,
            Dinheiro noGateway,
            Dinheiro emDisputa) {}

    record Periodo(
            LocalDate de,
            LocalDate ate,
            Dinheiro recebido,
            Dinheiro liberadoAoProfissional,
            Dinheiro receita,
            Dinheiro reembolsado,
            Dinheiro repassado) {}

    record Resumo(Saldos saldos, Periodo periodo) {}

    /** Uma diária no período: o que ela moveu e o que dela ainda está guardado ou em disputa. */
    record Linha(
            UUID id,
            UUID contratoId,
            long numero,
            LocalDate data,
            String tipo,
            String status,
            Dinheiro valor,
            Dinheiro comissao,
            Dinheiro guardado,
            Dinheiro emDisputa,
            Dinheiro liberadoAoProfissional,
            Dinheiro receita,
            Dinheiro reembolsado) {}

    record Pagina(LocalDate de, LocalDate ate, List<Linha> itens, boolean temMais) {}

    private record Intervalo(LocalDate de, LocalDate ate, OffsetDateTime inicio, OffsetDateTime fim) {}

    /** O que uma diária moveu no período, por conta. */
    private record Movido(BigDecimal liberado, BigDecimal receita, BigDecimal reembolsado) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    AdminFinanceiroService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Resumo resumo(LocalDate de, LocalDate ate) {
        Intervalo intervalo = intervalo(de, ate);
        return new Resumo(saldos(), periodo(intervalo));
    }

    @Transactional(readOnly = true)
    Pagina porDiaria(LocalDate de, LocalDate ate, UUID contrato, int pagina) {
        Intervalo intervalo = intervalo(de, ate);
        List<Linha> linhas = jdbc.query(
                """
                WITH contratos AS (
                    SELECT DISTINCT t.contrato_id
                      FROM transacao_financeira t
                     WHERE t.criado_em >= :inicio AND t.criado_em < :fim
                       AND t.contrato_id IS NOT NULL
                       AND (CAST(:contrato AS uuid) IS NULL OR t.contrato_id = :contrato)
                )
                SELECT d.id, d.contrato_id, c.numero, d.data, d.tipo, d.status, d.valor,
                       c.comissao_pct, c.taxa_paga_por,
                       COALESCE(m.liberado, 0) AS liberado, COALESCE(m.receita, 0) AS receita,
                       COALESCE(m.reembolsado, 0) AS reembolsado
                  FROM contratos x
                  JOIN contrato c ON c.id = x.contrato_id
                  JOIN diaria d ON d.contrato_id = x.contrato_id
                  LEFT JOIN LATERAL (
                      SELECT SUM(l.valor) FILTER (
                                 WHERE t.tipo = 'liberacao' AND r.tipo = 'profissional' AND l.natureza = 'C') AS liberado,
                             SUM(l.valor) FILTER (
                                 WHERE t.tipo = 'liberacao' AND r.tipo = 'receita_coe' AND l.natureza = 'C') AS receita,
                             SUM(l.valor) FILTER (
                                 WHERE t.tipo = 'reembolso' AND r.tipo = 'cliente' AND l.natureza = 'C') AS reembolsado
                        FROM transacao_financeira t
                        JOIN lancamento l ON l.transacao_id = t.id
                        JOIN conta_razao r ON r.id = l.conta_id
                       WHERE t.diaria_id = d.id AND t.criado_em >= :inicio AND t.criado_em < :fim
                  ) m ON true
                 ORDER BY d.data, d.id
                 LIMIT :limite OFFSET :pular
                """,
                new MapSqlParameterSource("inicio", intervalo.inicio())
                        .addValue("fim", intervalo.fim())
                        .addValue("contrato", contrato)
                        .addValue("limite", POR_PAGINA + 1)
                        .addValue("pular", (long) Math.clamp(pagina, 0, PAGINA_MAXIMA) * POR_PAGINA),
                (rs, n) -> linha(
                        rs.getObject("id", UUID.class),
                        rs.getObject("contrato_id", UUID.class),
                        rs.getLong("numero"),
                        rs.getObject("data", LocalDate.class),
                        rs.getString("tipo"),
                        rs.getString("status"),
                        CalculadoraDiaria.calcular(
                                new Dinheiro(rs.getBigDecimal("valor")),
                                new Percentual(rs.getBigDecimal("comissao_pct")),
                                TaxaPagaPor.deCodigo(rs.getString("taxa_paga_por"))),
                        new Movido(
                                rs.getBigDecimal("liberado"),
                                rs.getBigDecimal("receita"),
                                rs.getBigDecimal("reembolsado"))));
        boolean temMais = linhas.size() > POR_PAGINA;
        return new Pagina(intervalo.de(), intervalo.ate(), temMais ? linhas.subList(0, POR_PAGINA) : linhas, temMais);
    }

    private static Linha linha(
            UUID id,
            UUID contrato,
            long numero,
            LocalDate data,
            String tipo,
            String status,
            ValoresDiaria valores,
            Movido movido) {
        Dinheiro pago = valores.totalCliente();
        return new Linha(
                id,
                contrato,
                numero,
                data,
                tipo,
                status,
                valores.valorServico(),
                valores.comissao(),
                GUARDADAS.contains(status) ? pago : NADA,
                "contestada".equals(status) ? pago : NADA,
                reais(movido.liberado()),
                reais(movido.receita()),
                reais(movido.reembolsado()));
    }

    private Saldos saldos() {
        Map<String, Object> s = jdbc.queryForMap("""
                SELECT COALESCE(SUM(saldo) FILTER (WHERE tipo = 'custodia'), 0)     AS guardado,
                       COALESCE(SUM(saldo) FILTER (WHERE tipo = 'profissional'), 0) AS a_repassar,
                       COALESCE(SUM(saldo) FILTER (WHERE tipo = 'receita_coe'), 0)  AS receita,
                       COALESCE(SUM(saldo) FILTER (WHERE tipo = 'cliente'), 0)      AS a_estornar,
                       COALESCE(SUM(saldo) FILTER (WHERE tipo = 'gateway'), 0)      AS gateway
                  FROM saldo_conta
                """, Map.of());
        return new Saldos(
                reais((BigDecimal) s.get("guardado")),
                reais((BigDecimal) s.get("a_repassar")),
                reais((BigDecimal) s.get("receita")),
                reais((BigDecimal) s.get("a_estornar")),
                // a conta do gateway é devedora (créditos − débitos negativo): o dinheiro lá é o oposto do saldo
                reais(((BigDecimal) s.get("gateway")).negate()),
                emDisputa());
    }

    /** O que os clientes pagaram pelas diárias contestadas (segue na custódia até a decisão). */
    private Dinheiro emDisputa() {
        return jdbc
                .query(
                        """
                        SELECT d.valor, c.comissao_pct, c.taxa_paga_por
                          FROM diaria d JOIN contrato c ON c.id = d.contrato_id
                         WHERE d.status = 'contestada'
                        """,
                        Map.of(),
                        (rs, n) -> CalculadoraDiaria.calcular(
                                        new Dinheiro(rs.getBigDecimal("valor")),
                                        new Percentual(rs.getBigDecimal("comissao_pct")),
                                        TaxaPagaPor.deCodigo(rs.getString("taxa_paga_por")))
                                .totalCliente())
                .stream()
                .reduce(NADA, Dinheiro::somar);
    }

    private Periodo periodo(Intervalo intervalo) {
        Map<String, Object> p = jdbc.queryForMap(
                """
                SELECT COALESCE(SUM(l.valor) FILTER (
                           WHERE t.tipo = 'pagamento' AND r.tipo = 'custodia' AND l.natureza = 'C'), 0) AS recebido,
                       COALESCE(SUM(l.valor) FILTER (
                           WHERE t.tipo = 'liberacao' AND r.tipo = 'profissional' AND l.natureza = 'C'), 0) AS liberado,
                       COALESCE(SUM(l.valor) FILTER (
                           WHERE t.tipo = 'liberacao' AND r.tipo = 'receita_coe' AND l.natureza = 'C'), 0) AS receita,
                       COALESCE(SUM(l.valor) FILTER (
                           WHERE t.tipo = 'reembolso' AND r.tipo = 'cliente' AND l.natureza = 'C'), 0) AS reembolsado,
                       COALESCE(SUM(l.valor) FILTER (
                           WHERE t.tipo = 'repasse' AND r.tipo = 'profissional' AND l.natureza = 'D'), 0) AS repassado
                  FROM transacao_financeira t
                  JOIN lancamento l ON l.transacao_id = t.id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.criado_em >= :inicio AND t.criado_em < :fim
                """, new MapSqlParameterSource("inicio", intervalo.inicio()).addValue("fim", intervalo.fim()));
        return new Periodo(
                intervalo.de(),
                intervalo.ate(),
                reais((BigDecimal) p.get("recebido")),
                reais((BigDecimal) p.get("liberado")),
                reais((BigDecimal) p.get("receita")),
                reais((BigDecimal) p.get("reembolsado")),
                reais((BigDecimal) p.get("repassado")));
    }

    /** Dias inteiros no fuso de negócio: de 00:00 do {@code de} até 00:00 do dia seguinte ao {@code ate}. */
    private Intervalo intervalo(LocalDate de, LocalDate ate) {
        LocalDate fimDoPeriodo = ate != null ? ate : FusoDeNegocio.hoje(clock);
        LocalDate inicioDoPeriodo = de != null ? de : fimDoPeriodo.minusDays(DIAS_PADRAO);
        if (inicioDoPeriodo.isAfter(fimDoPeriodo)
                || ChronoUnit.DAYS.between(inicioDoPeriodo, fimDoPeriodo) > DIAS_MAXIMOS) {
            throw new RegraDeNegocioException(
                    "periodo-invalido", "Escolha um período de até um ano, com o início antes do fim.");
        }
        return new Intervalo(
                inicioDoPeriodo,
                fimDoPeriodo,
                inicioDoPeriodo.atStartOfDay(FusoDeNegocio.ZONA).toOffsetDateTime(),
                fimDoPeriodo.plusDays(1).atStartOfDay(FusoDeNegocio.ZONA).toOffsetDateTime());
    }

    private static Dinheiro reais(BigDecimal valor) {
        return new Dinheiro(valor.setScale(2));
    }
}
