package br.com.coe.servicos.avaliacao;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Avaliações dos dois lados do contrato (DOM-10; RN55, RN23). Avalia quem participa do contrato e teve pelo menos uma
 * diária liberada (paga e aprovada pelo app: pelo cliente, sozinha em 12 h ou pela decisão do admin); uma por lado
 * em cada contrato. A avaliação do cliente refaz a média do profissional na mesma transação ({@link
 * MediaDoProfissional}). {@code liberada} nunca volta atrás, por isso a checagem da RN55 não precisa de trava.
 */
@Service
class AvaliacaoService {

    private static final String NAO_ENCONTRADO = "Contrato não encontrado.";

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final MediaDoProfissional media;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    AvaliacaoService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioAutenticado usuario,
            MediaDoProfissional media,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.media = media;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** O contrato visto por um dos lados; de outro usuário (ou inexistente) = 404. */
    private record Lado(UUID contrato, UUID perfil, UUID avaliado, boolean souCliente) {}

    @Transactional
    AvaliacaoDtos.Avaliacao avaliar(AvaliacaoDtos.NovaAvaliacao pedido) {
        UUID eu = usuario.id();
        Lado lado = lado(pedido.contratoId(), eu);
        if (!temDiariaLiberada(lado.contrato())) {
            throw new RegraDeNegocioException(
                    "avaliacao-indisponivel", "Dá para avaliar depois que um dia do contrato for aprovado.");
        }
        String comentario = pedido.comentario() == null || pedido.comentario().isBlank()
                ? null
                : pedido.comentario().strip();
        if (comentario != null) {
            CensuraDeContato.exigirSemContato(comentario, "comentario");
        }
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UUID> criada = jdbc.queryForList(
                """
                INSERT INTO avaliacao (contrato_id, autor_id, avaliado_id, papel_autor, nota, comentario, criado_em)
                VALUES (:contrato, :eu, :avaliado, :papel, :nota, :comentario, :agora)
                ON CONFLICT (contrato_id, autor_id) DO NOTHING
                RETURNING id
                """,
                new MapSqlParameterSource("contrato", lado.contrato())
                        .addValue("eu", eu)
                        .addValue("avaliado", lado.avaliado())
                        .addValue("papel", lado.souCliente() ? "cliente" : "profissional")
                        .addValue("nota", pedido.nota())
                        .addValue("comentario", comentario)
                        .addValue("agora", agora),
                UUID.class);
        if (criada.isEmpty()) {
            throw new ConflitoException("avaliacao-ja-feita", "Você já avaliou este contrato.", "contratoId");
        }
        if (lado.souCliente()) {
            media.refazer(lado.perfil());
        }
        return new AvaliacaoDtos.Avaliacao(
                criada.get(0), lado.contrato(), pedido.nota(), comentario, agora.toInstant());
    }

    @Transactional(readOnly = true)
    AvaliacaoDtos.Situacao situacao(UUID contratoId) {
        UUID eu = usuario.id();
        Lado lado = lado(contratoId, eu);
        List<AvaliacaoDtos.Avaliacao> minha = jdbc.query(
                "SELECT id, nota, comentario, criado_em FROM avaliacao WHERE contrato_id = :contrato AND autor_id = :eu",
                new MapSqlParameterSource("contrato", lado.contrato()).addValue("eu", eu),
                (linha, n) -> new AvaliacaoDtos.Avaliacao(
                        linha.getObject("id", UUID.class),
                        lado.contrato(),
                        linha.getInt("nota"),
                        linha.getString("comentario"),
                        linha.getTimestamp("criado_em").toInstant()));
        AvaliacaoDtos.Avaliacao feita = minha.isEmpty() ? null : minha.get(0);
        return new AvaliacaoDtos.Situacao(feita == null && temDiariaLiberada(lado.contrato()), feita);
    }

    private Lado lado(UUID contrato, UUID eu) {
        List<Map<String, Object>> linhas =
                jdbc.queryForList("""
                SELECT c.id, c.cliente_id, p.id AS perfil, p.usuario_id
                  FROM contrato c
                  JOIN profissional p ON p.id = c.profissional_id
                 WHERE c.id = :contrato AND (c.cliente_id = :eu OR p.usuario_id = :eu)
                """, new MapSqlParameterSource("contrato", contrato).addValue("eu", eu));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        Map<String, Object> linha = linhas.get(0);
        boolean souCliente = eu.equals(linha.get("cliente_id"));
        return new Lado(
                (UUID) linha.get("id"),
                (UUID) linha.get("perfil"),
                souCliente ? (UUID) linha.get("usuario_id") : (UUID) linha.get("cliente_id"),
                souCliente);
    }

    /** RN55: pelo menos um dia pago e aprovado pelo app (liberada é o único caminho para isso). */
    private boolean temDiariaLiberada(UUID contrato) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM diaria WHERE contrato_id = :contrato AND status = 'liberada')",
                new MapSqlParameterSource("contrato", contrato),
                Boolean.class));
    }

    /** Avaliação como a equipe vê na moderação (nomes completos: só o admin chega aqui). */
    record NaModeracao(
            UUID id,
            UUID contratoId,
            int nota,
            String comentario,
            String papelAutor,
            String autor,
            String avaliado,
            boolean visivel,
            java.time.Instant criadoEm) {}

    record PaginaDaModeracao(List<NaModeracao> itens, boolean temMais) {}

    private static final int POR_PAGINA = 50;

    @Transactional(readOnly = true)
    PaginaDaModeracao listarParaModeracao(UUID profissional, int pagina) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("limite", POR_PAGINA + 1)
                .addValue("deslocamento", (long) Math.max(pagina, 0) * POR_PAGINA);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.contrato_id, a.nota, a.comentario, a.papel_autor, a.visivel, a.criado_em,
                       ua.nome AS autor, uv.nome AS avaliado
                  FROM avaliacao a
                  JOIN usuario ua ON ua.id = a.autor_id
                  JOIN usuario uv ON uv.id = a.avaliado_id
                """);
        if (profissional != null) {
            sql.append(" JOIN profissional pf ON pf.usuario_id = a.avaliado_id AND pf.id = :profissional");
            parametros.addValue("profissional", profissional);
        }
        sql.append(" ORDER BY a.criado_em DESC, a.id LIMIT :limite OFFSET :deslocamento");
        List<NaModeracao> itens = jdbc.query(
                sql.toString(),
                parametros,
                (linha, n) -> new NaModeracao(
                        linha.getObject("id", UUID.class),
                        linha.getObject("contrato_id", UUID.class),
                        linha.getInt("nota"),
                        linha.getString("comentario"),
                        linha.getString("papel_autor"),
                        linha.getString("autor"),
                        linha.getString("avaliado"),
                        linha.getBoolean("visivel"),
                        linha.getObject("criado_em", OffsetDateTime.class).toInstant()));
        boolean temMais = itens.size() > POR_PAGINA;
        return new PaginaDaModeracao(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    /** Ocultar ou mostrar de novo (RN59, auditado); a média do profissional é refeita na mesma transação. */
    @Transactional
    NaModeracao mudarVisibilidade(UUID id, boolean visivel) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT visivel, papel_autor, avaliado_id FROM avaliacao WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException("Avaliação não encontrada.");
        }
        Map<String, Object> linha = linhas.get(0);
        boolean antes = (Boolean) linha.get("visivel");
        if (antes != visivel) {
            jdbc.update(
                    "UPDATE avaliacao SET visivel = :visivel WHERE id = :id",
                    new MapSqlParameterSource("id", id).addValue("visivel", visivel));
            if ("cliente".equals(linha.get("papel_autor"))) {
                jdbc.queryForList(
                                "SELECT id FROM profissional WHERE usuario_id = :avaliado",
                                new MapSqlParameterSource("avaliado", linha.get("avaliado_id")),
                                UUID.class)
                        .forEach(media::refazer);
            }
            auditoria.registrar(
                    usuario.id(),
                    "ADMIN",
                    visivel ? "avaliacao.mostrar" : "avaliacao.ocultar",
                    "avaliacao",
                    id,
                    Map.of("visivel", antes),
                    Map.of("visivel", visivel),
                    null);
        }
        return jdbc.query(
                        """
                        SELECT a.id, a.contrato_id, a.nota, a.comentario, a.papel_autor, a.visivel, a.criado_em,
                               ua.nome AS autor, uv.nome AS avaliado
                          FROM avaliacao a
                          JOIN usuario ua ON ua.id = a.autor_id
                          JOIN usuario uv ON uv.id = a.avaliado_id
                         WHERE a.id = :id
                        """,
                        new MapSqlParameterSource("id", id),
                        (l, n) -> new NaModeracao(
                                l.getObject("id", UUID.class),
                                l.getObject("contrato_id", UUID.class),
                                l.getInt("nota"),
                                l.getString("comentario"),
                                l.getString("papel_autor"),
                                l.getString("autor"),
                                l.getString("avaliado"),
                                l.getBoolean("visivel"),
                                l.getObject("criado_em", OffsetDateTime.class).toInstant()))
                .get(0);
    }
}
