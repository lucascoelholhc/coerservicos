package br.com.coe.servicos.profissional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Consultas públicas da busca e do perfil (DOM-04, RN18 a RN23). Só perfil {@code ativo} de conta ativa (RN20).
 * Cidade atendida = dentro do raio da cidade base (Haversine, V10) ou marcada em {@code profissional_cidade}
 * (RN17). Dia livre = dia da semana que trabalha, sem bloqueio na agenda e sem diária ativa (a mesma regra do
 * índice {@code uq_diaria_agenda_profissional}). Os filtros entram no SQL só quando vêm no pedido, sempre como
 * parâmetro. Nenhuma coluna de contato ou dado pessoal é lida.
 */
@Component
class BuscaDeProfissionais {

    enum Ordem {
        LIVRE("proximo_livre ASC NULLS LAST, p.experiencia_anos DESC, p.id"),
        PRECO("p.valor_diaria ASC, proximo_livre ASC NULLS LAST, p.id"),
        EXPERIENCIA("p.experiencia_anos DESC, proximo_livre ASC NULLS LAST, p.id");

        private final String sql;

        Ordem(String sql) {
            this.sql = sql;
        }
    }

    /** Filtros já validados; {@code inicio}/{@code fim} é a janela dos dias livres. */
    record Filtro(
            String profissao,
            String servico,
            Integer cidade,
            LocalDate dia,
            Dinheiro valorMaximo,
            Integer experienciaMinima,
            boolean meiaDiaria,
            Ordem ordem,
            int limite,
            long deslocamento,
            LocalDate inicio,
            LocalDate fim) {}

    record Linha(
            UUID id,
            String nome,
            String profissao,
            List<String> outras,
            Dinheiro valorDiaria,
            Dinheiro valorMeiaDiaria,
            int experienciaAnos,
            BuscaResposta.Cidade cidade,
            BigDecimal distanciaKm,
            LocalDate proximoLivre,
            boolean selo,
            int totalAvaliacoes) {}

    record Dados(
            UUID id,
            String nome,
            String profissao,
            List<String> outras,
            int experienciaAnos,
            String ferramentas,
            String horario,
            Dinheiro valorDiaria,
            Dinheiro valorMeiaDiaria,
            BuscaResposta.Cidade cidade,
            int raioKm,
            List<String> frases,
            String bio,
            boolean selo,
            int totalAvaliacoes,
            BigDecimal notaMedia) {}

    record FotoGuardada(UUID id, String servico, String legenda, String chave) {}

    private static final String OUTRAS_PROFISSOES = """
            ARRAY(SELECT o.codigo FROM profissional_profissao pp JOIN profissao o ON o.id = pp.profissao_id
                   WHERE pp.profissional_id = p.id AND o.ativa ORDER BY o.ordem, o.codigo)""";

    private static final String PROXIMO_LIVRE = "(SELECT min(g::date) FROM generate_series(CAST(:inicio AS date),"
            + " CAST(:fim AS date), INTERVAL '1 day') AS g WHERE " + livre("p.id", "g::date") + ")";

    private final NamedParameterJdbcTemplate jdbc;

    BuscaDeProfissionais(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Livre no dia {@code dia} (expressão SQL de uma data) para o profissional {@code id} (expressão SQL). */
    private static String livre(String id, String dia) {
        return """
                (EXISTS (SELECT 1 FROM disponibilidade_semanal ds
                          WHERE ds.profissional_id = %1$s AND ds.dia_semana = EXTRACT(DOW FROM %2$s)::smallint)
                 AND NOT EXISTS (SELECT 1 FROM bloqueio_agenda b WHERE b.profissional_id = %1$s AND b.data = %2$s)
                 AND NOT EXISTS (SELECT 1 FROM diaria d WHERE d.profissional_id = %1$s AND d.data = %2$s
                                    AND d.status NOT IN ('cancelada', 'reembolsada')))""".formatted(id, dia);
    }

    List<Linha> buscar(Filtro filtro) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("inicio", filtro.inicio())
                .addValue("fim", filtro.fim())
                .addValue("limite", filtro.limite())
                .addValue("deslocamento", filtro.deslocamento());
        StringBuilder sql = new StringBuilder("SELECT p.id, u.nome, pr.codigo AS profissao, p.valor_diaria,"
                        + " p.valor_meia_diaria, p.experiencia_anos, cb.codigo_ibge, cb.nome AS cidade_nome, cb.uf,"
                        + " p.nr10_status = 'verificado' AS selo, p.total_avaliacoes, ")
                .append(OUTRAS_PROFISSOES)
                .append(" AS outras, ")
                .append(PROXIMO_LIVRE)
                .append(" AS proximo_livre, ");
        if (filtro.cidade() != null) {
            sql.append("fn_distancia_km(cb.latitude, cb.longitude, alvo.latitude, alvo.longitude) AS distancia_km");
        } else {
            sql.append("CAST(NULL AS numeric) AS distancia_km");
        }
        sql.append(" ").append("""
                 FROM profissional p
                 JOIN usuario u ON u.id = p.usuario_id AND u.status = 'ativo'
                 JOIN profissao pr ON pr.id = p.profissao_principal_id
                 JOIN cidade cb ON cb.id = p.cidade_base_id""");
        if (filtro.cidade() != null) {
            sql.append(" JOIN cidade alvo ON alvo.codigo_ibge = :cidade AND alvo.ativa");
            parametros.addValue("cidade", filtro.cidade());
        }
        sql.append(" WHERE p.status = 'ativo'");
        if (filtro.cidade() != null) {
            sql.append(" AND (fn_distancia_km(cb.latitude, cb.longitude, alvo.latitude, alvo.longitude) <= p.raio_km"
                    + " OR EXISTS (SELECT 1 FROM profissional_cidade pc"
                    + " WHERE pc.profissional_id = p.id AND pc.cidade_id = alvo.id))");
        }
        if (filtro.profissao() != null) {
            sql.append(" AND (pr.codigo = :profissao OR EXISTS (SELECT 1 FROM profissional_profissao pp"
                    + " JOIN profissao o ON o.id = pp.profissao_id"
                    + " WHERE pp.profissional_id = p.id AND o.codigo = :profissao))");
            parametros.addValue("profissao", filtro.profissao());
        }
        if (filtro.servico() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM profissional_servico ps JOIN servico s ON s.id = ps.servico_id"
                    + " WHERE ps.profissional_id = p.id AND s.ativo"
                    + " AND lower(f_sem_acento(s.nome)) = lower(f_sem_acento(:servico)))");
            parametros.addValue("servico", filtro.servico());
        }
        if (filtro.valorMaximo() != null) {
            sql.append(" AND p.valor_diaria <= :valorMaximo");
            parametros.addValue("valorMaximo", filtro.valorMaximo().valor());
        }
        if (filtro.experienciaMinima() != null) {
            sql.append(" AND p.experiencia_anos >= :experiencia");
            parametros.addValue("experiencia", filtro.experienciaMinima());
        }
        if (filtro.meiaDiaria()) {
            sql.append(" AND p.aceita_meia_diaria");
        }
        if (filtro.dia() != null) {
            sql.append(" AND ").append(livre("p.id", "CAST(:dia AS date)"));
            parametros.addValue("dia", filtro.dia());
        }
        sql.append(" ORDER BY ").append(filtro.ordem().sql).append(" LIMIT :limite OFFSET :deslocamento");
        return jdbc.query(
                sql.toString(),
                parametros,
                (linha, n) -> new Linha(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("profissao"),
                        textos(linha.getArray("outras")),
                        new Dinheiro(linha.getBigDecimal("valor_diaria")),
                        dinheiroOuNulo(linha.getBigDecimal("valor_meia_diaria")),
                        linha.getInt("experiencia_anos"),
                        cidade(linha),
                        linha.getBigDecimal("distancia_km"),
                        linha.getObject("proximo_livre", LocalDate.class),
                        linha.getBoolean("selo"),
                        linha.getInt("total_avaliacoes")));
    }

    /** Só de perfil ativo e conta ativa; senão vazio (o perfil responde 404 igual a inexistente). */
    Optional<Dados> dados(UUID id) {
        String sql = "SELECT p.id, u.nome, pr.codigo AS profissao, p.experiencia_anos, p.ferramentas, p.horario,"
                + " p.valor_diaria, p.valor_meia_diaria, cb.codigo_ibge, cb.nome AS cidade_nome, cb.uf, p.raio_km,"
                + " p.frases, p.bio, p.nr10_status = 'verificado' AS selo, p.total_avaliacoes, p.nota_media, "
                + OUTRAS_PROFISSOES + " AS outras "
                + """
                   FROM profissional p
                   JOIN usuario u ON u.id = p.usuario_id AND u.status = 'ativo'
                   JOIN profissao pr ON pr.id = p.profissao_principal_id
                   JOIN cidade cb ON cb.id = p.cidade_base_id
                  WHERE p.id = :id AND p.status = 'ativo'""";
        return jdbc
                .query(
                        sql,
                        new MapSqlParameterSource("id", id),
                        (linha, n) -> new Dados(
                                linha.getObject("id", UUID.class),
                                linha.getString("nome"),
                                linha.getString("profissao"),
                                textos(linha.getArray("outras")),
                                linha.getInt("experiencia_anos"),
                                linha.getString("ferramentas"),
                                linha.getString("horario"),
                                new Dinheiro(linha.getBigDecimal("valor_diaria")),
                                dinheiroOuNulo(linha.getBigDecimal("valor_meia_diaria")),
                                cidade(linha),
                                linha.getInt("raio_km"),
                                textos(linha.getArray("frases")),
                                linha.getString("bio"),
                                linha.getBoolean("selo"),
                                linha.getInt("total_avaliacoes"),
                                linha.getBigDecimal("nota_media")))
                .stream()
                .findFirst();
    }

    /** Serviços do perfil: os da profissão principal primeiro, na ordem do catálogo. */
    List<BuscaResposta.Servico> servicos(UUID id) {
        return jdbc.query(
                """
                SELECT o.codigo, s.nome
                  FROM profissional_servico ps
                  JOIN servico s ON s.id = ps.servico_id AND s.ativo
                  JOIN profissao o ON o.id = s.profissao_id
                  JOIN profissional p ON p.id = ps.profissional_id
                 WHERE ps.profissional_id = :id
                 ORDER BY (o.id = p.profissao_principal_id) DESC, o.ordem, s.ordem, s.nome""",
                new MapSqlParameterSource("id", id),
                (linha, n) -> new BuscaResposta.Servico(linha.getString("codigo"), linha.getString("nome")));
    }

    List<BuscaResposta.Cidade> outrasCidades(UUID id) {
        return jdbc.query("""
                SELECT c.codigo_ibge, c.nome AS cidade_nome, c.uf
                  FROM profissional_cidade pc JOIN cidade c ON c.id = pc.cidade_id AND c.ativa
                 WHERE pc.profissional_id = :id
                 ORDER BY c.uf, f_sem_acento(c.nome)""", new MapSqlParameterSource("id", id), (linha, n) -> cidade(linha));
    }

    List<Integer> diasDaSemana(UUID id) {
        return jdbc.queryForList(
                "SELECT dia_semana FROM disponibilidade_semanal WHERE profissional_id = :id ORDER BY dia_semana",
                new MapSqlParameterSource("id", id),
                Integer.class);
    }

    List<LocalDate> diasLivres(UUID id, LocalDate inicio, LocalDate fim) {
        return jdbc.queryForList(
                "SELECT g::date FROM generate_series(CAST(:inicio AS date), CAST(:fim AS date), INTERVAL '1 day') AS g"
                        + " WHERE " + livre("CAST(:id AS uuid)", "g::date") + " ORDER BY 1",
                new MapSqlParameterSource("id", id).addValue("inicio", inicio).addValue("fim", fim),
                LocalDate.class);
    }

    List<FotoGuardada> fotos(UUID id, int maximo) {
        return jdbc.query(
                """
                SELECT f.id, s.nome AS servico, f.legenda, f.chave_arquivo
                  FROM foto_portfolio f LEFT JOIN servico s ON s.id = f.servico_id
                 WHERE f.profissional_id = :id
                 ORDER BY f.ordem, f.id
                 LIMIT :maximo""",
                new MapSqlParameterSource("id", id).addValue("maximo", maximo),
                (linha, n) -> new FotoGuardada(
                        linha.getObject("id", UUID.class),
                        linha.getString("servico"),
                        linha.getString("legenda"),
                        linha.getString("chave_arquivo")));
    }

    private static BuscaResposta.Cidade cidade(ResultSet linha) throws SQLException {
        return new BuscaResposta.Cidade(
                linha.getInt("codigo_ibge"), linha.getString("cidade_nome"), linha.getString("uf"));
    }

    private static Dinheiro dinheiroOuNulo(BigDecimal valor) {
        return valor == null ? null : new Dinheiro(valor);
    }

    private static List<String> textos(Array array) throws SQLException {
        return array == null ? List.of() : List.copyOf(Arrays.asList((String[]) array.getArray()));
    }
}
