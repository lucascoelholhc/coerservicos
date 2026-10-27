package br.com.coe.servicos.contrato;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Área do profissional (FE-06): as diárias dele, o dinheiro (livro-caixa + o que ainda vai cair) e as folgas. O
 * profissional vem só do token; quem não tem perfil recebe 404.
 */
@Service
class AreaDoProfissionalService {

    /** Diárias passadas que ainda aparecem na lista (o "Dia a dia" do Dinheiro). */
    private static final int DIAS_PARA_TRAS = 60;
    /** Até quando dá para marcar folga (o mesmo alcance do filtro de dia da busca). */
    private static final int DIAS_DE_FOLGA = 90;

    private static final int MAXIMO_NA_LISTA = 200;

    private static final Dinheiro NADA = Dinheiro.de("0.00");

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final Clock clock;

    AreaDoProfissionalService(NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    AreaDoProfissionalDtos.Diarias diarias() {
        UUID perfil = meuPerfil();
        MapSqlParameterSource parametros = new MapSqlParameterSource("perfil", perfil)
                .addValue("desde", FusoDeNegocio.hoje(clock).minusDays(DIAS_PARA_TRAS))
                .addValue("maximo", MAXIMO_NA_LISTA);
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT d.id, d.contrato_id, c.numero, d.data, d.tipo, d.status, d.valor, c.comissao_pct, c.taxa_paga_por,
                       u.nome AS cliente_nome, ci.nome AS cidade_nome, ci.uf, d.chegou_em, d.terminou_em, d.auto_libera_em
                  FROM diaria d
                  JOIN contrato c ON c.id = d.contrato_id
                  JOIN usuario u ON u.id = c.cliente_id
                  JOIN cidade ci ON ci.id = c.cidade_id
                 WHERE d.profissional_id = :perfil AND d.data >= :desde AND d.status <> 'cancelada'
                 ORDER BY d.data, d.id
                 LIMIT :maximo
                """, parametros);
        Map<UUID, List<String>> servicos = servicosDos(linhas);
        return new AreaDoProfissionalDtos.Diarias(linhas.stream()
                .map(d -> new AreaDoProfissionalDtos.Diaria(
                        (UUID) d.get("id"),
                        (UUID) d.get("contrato_id"),
                        ((Number) d.get("numero")).longValue(),
                        ((java.sql.Date) d.get("data")).toLocalDate(),
                        (String) d.get("tipo"),
                        (String) d.get("status"),
                        repasse(d),
                        NomeDeExibicao.de((String) d.get("cliente_nome")),
                        new AreaDoProfissionalDtos.Cidade((String) d.get("cidade_nome"), (String) d.get("uf")),
                        servicos.getOrDefault((UUID) d.get("contrato_id"), List.of()),
                        instante(d.get("chegou_em")),
                        instante(d.get("terminou_em")),
                        "aguardando".equals(d.get("status")) ? instante(d.get("auto_libera_em")) : null))
                .toList());
    }

    @Transactional(readOnly = true)
    AreaDoProfissionalDtos.Saldo dinheiro() {
        MapSqlParameterSource eu = new MapSqlParameterSource("perfil", meuPerfil());
        BigDecimal liberado = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE l.natureza WHEN 'C' THEN l.valor ELSE -l.valor END), 0)
                  FROM lancamento l
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE r.tipo = 'profissional' AND r.profissional_id = :perfil
                """, eu, BigDecimal.class);
        Dinheiro vaiCair = NADA;
        Dinheiro emReclamacao = NADA;
        List<Map<String, Object>> pendentes = jdbc.queryForList("""
                SELECT d.status, d.valor, c.comissao_pct, c.taxa_paga_por
                  FROM diaria d JOIN contrato c ON c.id = d.contrato_id
                 WHERE d.profissional_id = :perfil AND d.status IN ('paga', 'andamento', 'aguardando', 'contestada')
                """, eu);
        for (Map<String, Object> d : pendentes) {
            if ("contestada".equals(d.get("status"))) {
                emReclamacao = emReclamacao.somar(repasse(d));
            } else {
                vaiCair = vaiCair.somar(repasse(d));
            }
        }
        return new AreaDoProfissionalDtos.Saldo(new Dinheiro(liberado.setScale(2)), vaiCair, emReclamacao);
    }

    @Transactional(readOnly = true)
    AreaDoProfissionalDtos.Agenda agenda() {
        LocalDate hoje = FusoDeNegocio.hoje(clock);
        LocalDate ate = hoje.plusDays(DIAS_DE_FOLGA);
        MapSqlParameterSource parametros = new MapSqlParameterSource("perfil", meuPerfil())
                .addValue("hoje", hoje)
                .addValue("ate", ate);
        List<Integer> semana = jdbc.queryForList(
                "SELECT dia_semana FROM disponibilidade_semanal WHERE profissional_id = :perfil ORDER BY dia_semana",
                parametros,
                Integer.class);
        List<LocalDate> folgas =
                jdbc.query("""
                SELECT data FROM bloqueio_agenda
                 WHERE profissional_id = :perfil AND data BETWEEN :hoje AND :ate ORDER BY data
                """, parametros, (ResultSet linha, int n) -> linha.getObject("data", LocalDate.class));
        return new AreaDoProfissionalDtos.Agenda(semana, folgas, ate);
    }

    /**
     * Marca folga num dia livre (repetir não muda nada). Dia com diária ativa = 409: a folga não desmarca quem já
     * contratou. A trava da agenda é a mesma da contratação ({@link #travarAgenda}).
     */
    @Transactional
    void marcarFolga(LocalDate data) {
        UUID perfil = meuPerfil();
        exigirDiaDeFolga(data);
        travarAgenda(jdbc, perfil);
        MapSqlParameterSource dia = new MapSqlParameterSource("perfil", perfil).addValue("data", data);
        Integer ocupadas = jdbc.queryForObject("""
                SELECT count(*) FROM diaria
                 WHERE profissional_id = :perfil AND data = :data AND status NOT IN ('cancelada', 'reembolsada')
                """, dia, Integer.class);
        if (ocupadas != null && ocupadas > 0) {
            throw new ConflitoException(
                    "dia-com-diaria", "Esse dia já tem uma diária marcada. Fale com o cliente pelo contrato.", "data");
        }
        jdbc.update("""
                INSERT INTO bloqueio_agenda (profissional_id, data, motivo) VALUES (:perfil, :data, 'folga')
                ON CONFLICT ON CONSTRAINT uq_bloqueio_agenda DO NOTHING
                """, dia);
    }

    @Transactional
    void tirarFolga(LocalDate data) {
        UUID perfil = meuPerfil();
        exigirDiaDeFolga(data);
        jdbc.update(
                "DELETE FROM bloqueio_agenda WHERE profissional_id = :perfil AND data = :data",
                new MapSqlParameterSource("perfil", perfil).addValue("data", data));
    }

    /**
     * Trava a agenda do profissional até o fim da transação: folga e contratação do mesmo profissional passam uma de
     * cada vez (sem isso, um dia podia virar folga e diária ao mesmo tempo).
     */
    static void travarAgenda(NamedParameterJdbcTemplate jdbc, UUID perfil) {
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(:chave, 0))",
                new MapSqlParameterSource("chave", "agenda:" + perfil),
                Object.class);
    }

    private void exigirDiaDeFolga(LocalDate data) {
        LocalDate amanha = FusoDeNegocio.hoje(clock).plusDays(1);
        if (data.isBefore(amanha) || data.isAfter(amanha.plusDays(DIAS_DE_FOLGA - 1L))) {
            throw new RegraDeNegocioException(
                    "dia-invalido", "Escolha um dia de amanhã até " + DIAS_DE_FOLGA + " dias para frente.");
        }
    }

    private UUID meuPerfil() {
        return jdbc
                .queryForList(
                        "SELECT id FROM profissional WHERE usuario_id = :eu",
                        new MapSqlParameterSource("eu", usuario.id()),
                        UUID.class)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de profissional não encontrado."));
    }

    private Map<UUID, List<String>> servicosDos(List<Map<String, Object>> linhas) {
        Map<UUID, List<String>> servicos = new HashMap<>();
        if (linhas.isEmpty()) {
            return servicos;
        }
        jdbc.query(
                """
                SELECT cs.contrato_id, s.nome FROM contrato_servico cs JOIN servico s ON s.id = cs.servico_id
                 WHERE cs.contrato_id IN (:contratos) ORDER BY s.ordem, s.nome
                """,
                new MapSqlParameterSource(
                        "contratos",
                        linhas.stream()
                                .map(l -> l.get("contrato_id"))
                                .distinct()
                                .toList()),
                (RowCallbackHandler) linha -> servicos.computeIfAbsent(
                                linha.getObject("contrato_id", UUID.class), k -> new ArrayList<>())
                        .add(linha.getString("nome")));
        return servicos;
    }

    /** O repasse do dia pela calculadora, com a comissão e a taxa congeladas no contrato (nunca as de hoje). */
    private static Dinheiro repasse(Map<String, Object> d) {
        return CalculadoraDiaria.calcular(
                        new Dinheiro((BigDecimal) d.get("valor")),
                        new Percentual((BigDecimal) d.get("comissao_pct")),
                        TaxaPagaPor.deCodigo((String) d.get("taxa_paga_por")))
                .repasseProfissional();
    }

    private static Instant instante(Object valor) {
        if (valor instanceof Timestamp momento) {
            return momento.toInstant();
        }
        return valor instanceof OffsetDateTime momento ? momento.toInstant() : null;
    }
}
