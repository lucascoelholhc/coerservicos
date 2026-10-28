package br.com.coe.servicos.moderacao;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

/**
 * Denúncias (DOM-11; RN59): quem está numa conversa denuncia o outro lado (o alvo vem da conversa, nunca do pedido);
 * a equipe vê a fila, mais antiga primeiro, e decide procedente ou improcedente uma vez. Decidir não suspende ninguém
 * sozinho: suspender é outra ação, manual e auditada.
 */
@Service
class ModeracaoService {

    static final int POR_PAGINA = 50;
    private static final String NAO_ENCONTRADA = "Denúncia não encontrada.";

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    ModeracaoService(
            NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, RegistroDeAuditoria auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    record NovaDenuncia(
            @NotNull UUID conversaId,
            UUID mensagemId,

            @NotNull @Pattern(regexp = "contato_fora|golpe|ofensa|perfil_falso|outro")
            String motivo,

            @Size(max = 1000) String descricao) {}

    record Criada(UUID id, String status) {}

    record Pessoa(UUID id, String nome) {}

    record Mensagem(UUID id, String texto, boolean bloqueada) {}

    record Denuncia(
            UUID id,
            String motivo,
            String descricao,
            String status,
            Instant criadoEm,
            Pessoa denunciante,
            Pessoa alvo,
            Mensagem mensagem) {}

    record Fila(List<Denuncia> itens, boolean temMais) {}

    record Decisao(
            @NotNull @Pattern(regexp = "procedente|improcedente")
            String resultado) {}

    @Transactional
    Criada denunciar(NovaDenuncia pedido) {
        UUID eu = usuario.id();
        List<UUID> alvos = jdbc.queryForList(
                """
                SELECT CASE WHEN c.cliente_id = :eu THEN p.usuario_id ELSE c.cliente_id END
                  FROM conversa c
                  JOIN profissional p ON p.id = c.profissional_id
                 WHERE c.id = :conversa AND (c.cliente_id = :eu OR p.usuario_id = :eu)
                """, new MapSqlParameterSource("conversa", pedido.conversaId()).addValue("eu", eu), UUID.class);
        if (alvos.isEmpty()) {
            throw new RecursoNaoEncontradoException("Conversa não encontrada.");
        }
        UUID alvo = alvos.get(0);
        if (pedido.mensagemId() != null) {
            Boolean doAlvo = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM mensagem WHERE id = :id AND conversa_id = :conversa AND autor_id = :alvo)",
                    new MapSqlParameterSource("id", pedido.mensagemId())
                            .addValue("conversa", pedido.conversaId())
                            .addValue("alvo", alvo),
                    Boolean.class);
            if (!Boolean.TRUE.equals(doAlvo)) {
                throw new RecursoNaoEncontradoException("Mensagem não encontrada.");
            }
        }
        String descricao = pedido.descricao() == null || pedido.descricao().isBlank()
                ? null
                : pedido.descricao().strip();
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO denuncia (denunciante_id, alvo_usuario_id, mensagem_id, motivo, descricao, criado_em)
                VALUES (:eu, :alvo, :mensagem, :motivo, :descricao, :agora)
                RETURNING id
                """,
                new MapSqlParameterSource("eu", eu)
                        .addValue("alvo", alvo)
                        .addValue("mensagem", pedido.mensagemId())
                        .addValue("motivo", pedido.motivo())
                        .addValue("descricao", descricao)
                        .addValue("agora", agora()),
                UUID.class);
        return new Criada(id, "aberta");
    }

    @Transactional(readOnly = true)
    Fila fila(int pagina) {
        List<Denuncia> itens = jdbc.query(
                """
                SELECT d.id, d.motivo, d.descricao, d.status, d.criado_em,
                       d.denunciante_id, ud.nome AS denunciante_nome, d.alvo_usuario_id, ua.nome AS alvo_nome,
                       m.id AS mensagem_id, m.texto, m.bloqueada
                  FROM denuncia d
                  JOIN usuario ud ON ud.id = d.denunciante_id
                  JOIN usuario ua ON ua.id = d.alvo_usuario_id
                  LEFT JOIN mensagem m ON m.id = d.mensagem_id
                 WHERE d.status IN ('aberta', 'em_analise')
                 ORDER BY d.criado_em, d.id
                 LIMIT :limite OFFSET :deslocamento
                """,
                new MapSqlParameterSource("limite", POR_PAGINA + 1)
                        .addValue("deslocamento", (long) Math.max(pagina, 0) * POR_PAGINA),
                (linha, n) -> new Denuncia(
                        linha.getObject("id", UUID.class),
                        linha.getString("motivo"),
                        linha.getString("descricao"),
                        linha.getString("status"),
                        linha.getObject("criado_em", OffsetDateTime.class).toInstant(),
                        new Pessoa(linha.getObject("denunciante_id", UUID.class), linha.getString("denunciante_nome")),
                        new Pessoa(linha.getObject("alvo_usuario_id", UUID.class), linha.getString("alvo_nome")),
                        linha.getObject("mensagem_id", UUID.class) == null
                                ? null
                                : new Mensagem(
                                        linha.getObject("mensagem_id", UUID.class),
                                        linha.getString("texto"),
                                        linha.getBoolean("bloqueada"))));
        boolean temMais = itens.size() > POR_PAGINA;
        return new Fila(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional
    Criada decidir(UUID id, Decisao pedido) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT status, alvo_usuario_id, denunciante_id FROM denuncia WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        String antes = (String) linhas.get(0).get("status");
        if (usuario.id().equals(linhas.get(0).get("alvo_usuario_id"))
                || usuario.id().equals(linhas.get(0).get("denunciante_id"))) {
            throw new AcaoProibidaException("acao-propria", "Outra pessoa da equipe precisa decidir essa denúncia.");
        }
        if (!"aberta".equals(antes) && !"em_analise".equals(antes)) {
            throw new ConflitoException("denuncia-decidida", "Essa denúncia já foi decidida. Atualize a fila.", null);
        }
        jdbc.update(
                "UPDATE denuncia SET status = :status, tratada_por = :admin, tratada_em = :agora WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("status", pedido.resultado())
                        .addValue("admin", usuario.id())
                        .addValue("agora", agora()));
        auditoria.registrar(
                usuario.id(),
                "ADMIN",
                "moderacao.denuncia-decidir",
                "denuncia",
                id,
                Map.of("status", antes),
                Map.of("status", pedido.resultado()),
                null);
        return new Criada(id, pedido.resultado());
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
