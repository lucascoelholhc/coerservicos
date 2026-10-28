package br.com.coe.servicos.profissional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Profissionais no painel do admin (DOM-11; RN44d, RN44e, RF52): lista com busca, faltas na janela e alerta
 * ({@code FALTAS_ALERTA} em {@code FALTAS_JANELA_DIAS}); inativar e reativar só pela mão do admin, com motivo (na
 * linha, nunca na auditoria). Nenhuma quantidade de faltas muda o status sozinha. Contratos já pagos não mudam com a
 * suspensão (o que fazer com eles fica para a equipe decidir caso a caso).
 */
@Service
class AdminProfissionaisService {

    static final int POR_PAGINA = 50;
    private static final String NAO_ENCONTRADO = "Profissional não encontrado.";

    private final NamedParameterJdbcTemplate jdbc;
    private final ConfiguracaoNegocio configuracao;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    AdminProfissionaisService(
            NamedParameterJdbcTemplate jdbc,
            ConfiguracaoNegocio configuracao,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.jdbc = jdbc;
        this.configuracao = configuracao;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    record Cidade(String nome, String uf) {}

    record Item(
            UUID id,
            UUID usuarioId,
            String nome,
            String profissao,
            Cidade cidade,
            String status,
            int faltasNaJanela,
            boolean alertaFaltas,
            BigDecimal notaMedia,
            int totalAvaliacoes) {}

    record Pagina(List<Item> itens, boolean temMais) {}

    record Falta(UUID diariaId, UUID disputaId, LocalDate data, Instant registradaEm) {}

    record Detalhe(
            UUID id,
            UUID usuarioId,
            String nome,
            String profissao,
            Cidade cidade,
            String status,
            String motivoSuspensao,
            Instant suspensoEm,
            int faltasNaJanela,
            boolean alertaFaltas,
            List<Falta> faltas,
            BigDecimal notaMedia,
            int totalAvaliacoes) {}

    record Suspensao(@NotBlank @Size(min = 10, max = 300) String motivo) {}

    private static final String COLUNAS = """
            SELECT p.id, p.usuario_id, u.nome, pr.nome AS profissao, c.nome AS cidade, c.uf, p.status,
                   p.motivo_suspensao, p.suspenso_em, p.nota_media, p.total_avaliacoes,
                   (SELECT count(*) FROM ocorrencia_profissional o
                     WHERE o.profissional_id = p.id AND o.criado_em >= :desde)::int AS faltas
              FROM profissional p
              JOIN usuario u ON u.id = p.usuario_id
              LEFT JOIN profissao pr ON pr.id = p.profissao_principal_id
              LEFT JOIN cidade c ON c.id = p.cidade_base_id
            """;

    @Transactional(readOnly = true)
    Pagina listar(String busca, String status, boolean soComAlerta, int pagina) {
        int alerta = configuracao.faltasAlerta();
        MapSqlParameterSource parametros = new MapSqlParameterSource("desde", desde())
                .addValue("limite", POR_PAGINA + 1)
                .addValue("deslocamento", (long) Math.max(pagina, 0) * POR_PAGINA)
                .addValue("alerta", alerta);
        StringBuilder sql =
                new StringBuilder("SELECT * FROM (").append(COLUNAS).append(" WHERE p.status <> 'rascunho'");
        if (busca != null && !busca.isBlank()) {
            sql.append(" AND f_sem_acento(u.nome) ILIKE '%' || f_sem_acento(:busca) || '%' ESCAPE '\\'");
            parametros.addValue("busca", semCuringas(busca.strip()));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = :status");
            parametros.addValue("status", status.strip());
        }
        sql.append(") x");
        if (soComAlerta) {
            sql.append(" WHERE x.faltas >= :alerta");
        }
        sql.append(" ORDER BY x.faltas DESC, x.nome, x.id LIMIT :limite OFFSET :deslocamento");
        List<Item> itens = jdbc.query(
                sql.toString(),
                parametros,
                (linha, n) -> new Item(
                        linha.getObject("id", UUID.class),
                        linha.getObject("usuario_id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("profissao"),
                        cidade(linha.getString("cidade"), linha.getString("uf")),
                        linha.getString("status"),
                        linha.getInt("faltas"),
                        linha.getInt("faltas") >= alerta,
                        linha.getBigDecimal("nota_media"),
                        linha.getInt("total_avaliacoes")));
        boolean temMais = itens.size() > POR_PAGINA;
        return new Pagina(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional(readOnly = true)
    Detalhe detalhe(UUID id) {
        int alerta = configuracao.faltasAlerta();
        List<Detalhe> achados = jdbc.query(
                COLUNAS + " WHERE p.id = :id AND p.status <> 'rascunho'",
                new MapSqlParameterSource("id", id).addValue("desde", desde()),
                (linha, n) -> new Detalhe(
                        linha.getObject("id", UUID.class),
                        linha.getObject("usuario_id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("profissao"),
                        cidade(linha.getString("cidade"), linha.getString("uf")),
                        linha.getString("status"),
                        linha.getString("motivo_suspensao"),
                        instante(linha.getObject("suspenso_em", OffsetDateTime.class)),
                        linha.getInt("faltas"),
                        linha.getInt("faltas") >= alerta,
                        faltas(id),
                        linha.getBigDecimal("nota_media"),
                        linha.getInt("total_avaliacoes")));
        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        return achados.get(0);
    }

    /** RN44e: só de ativo ou pausado; o perfil sai da busca na hora (só {@code ativo} aparece). */
    @Transactional
    Detalhe suspender(UUID id, Suspensao pedido) {
        String antes = travar(id);
        if (!"ativo".equals(antes) && !"pausado".equals(antes)) {
            throw new ConflitoException(
                    "status-invalido", "Só dá para suspender um profissional ativo ou pausado.", null);
        }
        jdbc.update(
                """
                UPDATE profissional SET status = 'suspenso', motivo_suspensao = :motivo, suspenso_em = :agora
                 WHERE id = :id
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("motivo", pedido.motivo().strip())
                        .addValue("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        auditar("profissional.suspender", id, antes, "suspenso");
        return detalhe(id);
    }

    /** RN44e: reverter a suspensão volta a {@code ativo} e limpa o motivo. */
    @Transactional
    Detalhe reativar(UUID id) {
        String antes = travar(id);
        if (!"suspenso".equals(antes)) {
            throw new ConflitoException("status-invalido", "Só dá para reativar um profissional suspenso.", null);
        }
        jdbc.update(
                "UPDATE profissional SET status = 'ativo', motivo_suspensao = NULL, suspenso_em = NULL WHERE id = :id",
                new MapSqlParameterSource("id", id));
        auditar("profissional.reativar", id, antes, "ativo");
        return detalhe(id);
    }

    /** Trava a linha (dois admins ao mesmo tempo passam um de cada vez) e devolve o status atual. */
    private String travar(UUID id) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT usuario_id, status FROM profissional WHERE id = :id AND status <> 'rascunho' FOR NO KEY UPDATE",
                new MapSqlParameterSource("id", id));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        if (usuario.id().equals(linhas.get(0).get("usuario_id"))) {
            throw new AcaoProibidaException(
                    "acao-propria", "Outra pessoa da equipe precisa decidir sobre o seu perfil.");
        }
        return (String) linhas.get(0).get("status");
    }

    private void auditar(String acao, UUID id, String antes, String depois) {
        auditoria.registrar(
                usuario.id(),
                "ADMIN",
                acao,
                "profissional",
                id,
                Map.of("status", antes),
                Map.of("status", depois),
                null);
    }

    private List<Falta> faltas(UUID id) {
        return jdbc.query(
                """
                SELECT o.diaria_id, o.disputa_id, d.data, o.criado_em
                  FROM ocorrencia_profissional o
                  JOIN diaria d ON d.id = o.diaria_id
                 WHERE o.profissional_id = :id
                 ORDER BY o.criado_em DESC, o.id
                 LIMIT 50
                """,
                new MapSqlParameterSource("id", id),
                (linha, n) -> new Falta(
                        linha.getObject("diaria_id", UUID.class),
                        linha.getObject("disputa_id", UUID.class),
                        linha.getObject("data", LocalDate.class),
                        instante(linha.getObject("criado_em", OffsetDateTime.class))));
    }

    private OffsetDateTime desde() {
        return OffsetDateTime.ofInstant(clock.instant().minus(configuracao.faltasJanela()), ZoneOffset.UTC);
    }

    private static Cidade cidade(String nome, String uf) {
        return nome == null ? null : new Cidade(nome, uf);
    }

    private static Instant instante(OffsetDateTime quando) {
        return quando == null ? null : quando.toInstant();
    }

    /** O texto da busca entra no LIKE como texto, nunca como curinga. */
    static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
