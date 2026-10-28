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
    private final Clock clock;

    AvaliacaoService(
            NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, MediaDoProfissional media, Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.media = media;
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
}
