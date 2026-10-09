package br.com.coe.servicos.catalogo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Três consultas, uma por bloco (profissões com a área, serviços, cidades), sem N+1. A ordem das
 * cidades usa {@code f_sem_acento(nome) COLLATE "C"} (função da V1) para não depender da collation
 * do banco (acento, espaço e hífen ordenam igual no Windows, no Testcontainers e no RDS).
 */
@Component
class FonteCatalogoJdbc implements FonteCatalogo {

    private static final String PROFISSOES = """
            SELECT p.id, a.codigo AS area_codigo, a.nome AS area_nome,
                   p.codigo, p.nome, p.nome_plural, p.icone
            FROM profissao p
            JOIN area a ON a.id = p.area_id
            WHERE p.ativa
            ORDER BY a.ordem, a.codigo, p.ordem, p.codigo
            """;
    private static final String SERVICOS = """
            SELECT s.profissao_id, s.nome
            FROM servico s
            JOIN profissao p ON p.id = s.profissao_id
            WHERE s.ativo AND p.ativa
            ORDER BY s.ordem, s.nome
            """;
    private static final String CIDADES = """
            SELECT codigo_ibge, nome, uf
            FROM cidade
            WHERE ativa
            ORDER BY uf, f_sem_acento(nome) COLLATE "C", codigo_ibge
            """;

    private record LinhaProfissao(
            UUID id, String areaCodigo, String areaNome, String codigo, String nome, String nomePlural, String icone) {}

    private final JdbcTemplate jdbc;

    FonteCatalogoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CatalogoResposta ler() {
        List<LinhaProfissao> profissoes = jdbc.query(
                PROFISSOES,
                (linha, numero) -> new LinhaProfissao(
                        linha.getObject("id", UUID.class),
                        linha.getString("area_codigo"),
                        linha.getString("area_nome"),
                        linha.getString("codigo"),
                        linha.getString("nome"),
                        linha.getString("nome_plural"),
                        linha.getString("icone")));

        Map<UUID, List<CatalogoResposta.Servico>> servicos = new HashMap<>();
        jdbc.query(SERVICOS, linha -> {
            servicos.computeIfAbsent(linha.getObject("profissao_id", UUID.class), id -> new ArrayList<>())
                    .add(new CatalogoResposta.Servico(linha.getString("nome")));
        });

        List<CatalogoResposta.Cidade> cidades = jdbc.query(
                CIDADES,
                (linha, numero) -> new CatalogoResposta.Cidade(
                        linha.getInt("codigo_ibge"), linha.getString("nome"), linha.getString("uf")));

        return new CatalogoResposta(agruparPorArea(profissoes, servicos), cidades);
    }

    /** Áreas na ordem da primeira profissão de cada uma; área sem profissão ativa nem chega aqui. */
    private static List<CatalogoResposta.Area> agruparPorArea(
            List<LinhaProfissao> profissoes, Map<UUID, List<CatalogoResposta.Servico>> servicos) {
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, List<CatalogoResposta.Profissao>> porArea = new LinkedHashMap<>();
        for (LinhaProfissao linha : profissoes) {
            nomes.putIfAbsent(linha.areaCodigo(), linha.areaNome());
            porArea.computeIfAbsent(linha.areaCodigo(), codigo -> new ArrayList<>())
                    .add(new CatalogoResposta.Profissao(
                            linha.codigo(),
                            linha.nome(),
                            linha.nomePlural(),
                            linha.icone(),
                            servicos.getOrDefault(linha.id(), List.of())));
        }
        List<CatalogoResposta.Area> areas = new ArrayList<>();
        porArea.forEach((codigo, lista) -> areas.add(new CatalogoResposta.Area(codigo, nomes.get(codigo), lista)));
        return areas;
    }
}
