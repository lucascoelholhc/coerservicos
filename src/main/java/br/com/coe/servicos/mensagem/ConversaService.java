package br.com.coe.servicos.mensagem;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Chat entre cliente e profissional (DOM-05, RN45–RN50). Antes do contato liberado (contrato pago), toda mensagem
 * passa pela censura: a que tem contato não é entregue, fica guardada mascarada (o autor vê o motivo) e vira uma
 * tentativa; passou de {@code TENTATIVAS_ANTES_ANALISE}, a conta vai para análise e fica sem chat até o admin decidir.
 * Quem não participa da conversa recebe 404, igual a uma conversa que não existe.
 */
@Service
class ConversaService {

    private static final int MENSAGENS_POR_VEZ = 100;
    private static final int CONVERSAS_POR_VEZ = 100;
    private static final int TRECHO_MAXIMO = 200;
    private static final String NAO_ENCONTRADA = "Conversa não encontrada.";
    /** Regras que são o próprio contato (palavras como "zap" ou "me liga" podem ficar no texto guardado). */
    private static final java.util.Set<String> DADOS_DE_CONTATO =
            java.util.Set.of("telefone", "email", "link", "perfil");

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final ConfiguracaoNegocio configuracao;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    ConversaService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioAutenticado usuario,
            ConfiguracaoNegocio configuracao,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** A conversa aberta e se ela é nova (201) ou já existia (200). */
    record Aberta(ChatDtos.Conversa conversa, boolean criada) {}

    @Transactional
    Aberta abrir(UUID profissionalId) {
        UUID eu = usuario.id();
        exigirChatLiberado(travarConta(eu));
        List<Map<String, Object>> perfil = jdbc.queryForList("""
                SELECT p.usuario_id, u.nome FROM profissional p JOIN usuario u ON u.id = p.usuario_id
                 WHERE p.id = :id AND p.status = 'ativo' AND u.status = 'ativo'
                """, new MapSqlParameterSource("id", profissionalId));
        if (perfil.isEmpty()) {
            throw new RecursoNaoEncontradoException("Profissional não encontrado.");
        }
        if (eu.equals(perfil.get(0).get("usuario_id"))) {
            throw new RegraDeNegocioException("conversa-propria", "Você não pode abrir uma conversa com você mesmo.");
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("cliente", eu)
                .addValue("profissional", profissionalId)
                .addValue("agora", agora());
        List<UUID> nova = jdbc.queryForList("""
                INSERT INTO conversa (cliente_id, profissional_id, criado_em) VALUES (:cliente, :profissional, :agora)
                ON CONFLICT (cliente_id, profissional_id) DO NOTHING RETURNING id
                """, parametros, UUID.class);
        UUID id = nova.isEmpty()
                ? jdbc.queryForObject(
                        "SELECT id FROM conversa WHERE cliente_id = :cliente AND profissional_id = :profissional",
                        parametros,
                        UUID.class)
                : nova.get(0);
        return new Aberta(minhas(eu, id).get(0), !nova.isEmpty());
    }

    @Transactional(readOnly = true)
    ChatDtos.Conversas listar() {
        return new ChatDtos.Conversas(minhas(usuario.id(), null));
    }

    @Transactional(readOnly = true)
    ChatDtos.Mensagens mensagens(UUID conversa, UUID antes) {
        UUID eu = usuario.id();
        participar(conversa, eu);
        MapSqlParameterSource parametros = new MapSqlParameterSource("conversa", conversa)
                .addValue("eu", eu)
                .addValue("antes", antes)
                .addValue("limite", MENSAGENS_POR_VEZ);
        List<ChatDtos.Mensagem> itens = new ArrayList<>(jdbc.query("""
                SELECT id, autor_id, texto, bloqueada, regras_violadas, lida_em, criado_em FROM mensagem
                 WHERE conversa_id = :conversa AND (NOT bloqueada OR autor_id = :eu)
                   AND (CAST(:antes AS uuid) IS NULL
                        OR (criado_em, id) < (SELECT a.criado_em, a.id FROM mensagem a
                                               WHERE a.id = CAST(:antes AS uuid) AND a.conversa_id = :conversa))
                 ORDER BY criado_em DESC, id DESC LIMIT :limite
                """, parametros, (linha, n) -> mensagem(linha, eu)));
        Collections.reverse(itens);
        return new ChatDtos.Mensagens(itens);
    }

    @Transactional
    ChatDtos.Envio enviar(UUID conversa, String texto) {
        UUID eu = usuario.id();
        boolean liberado = participar(conversa, eu);
        // a linha da conta em trava: duas mensagens juntas não contam a mesma tentativa nem pulam a análise
        exigirChatLiberado(travarConta(eu));
        String limpo = texto.strip();
        List<String> regras = liberado ? List.of() : CensuraDeContato.regrasVioladas(limpo);
        if (regras.isEmpty()) {
            ChatDtos.Mensagem entregue = gravar(conversa, eu, limpo, regras);
            jdbc.update(
                    "UPDATE conversa SET ultima_mensagem_em = :agora WHERE id = :id",
                    new MapSqlParameterSource("agora", agora()).addValue("id", conversa));
            return new ChatDtos.Envio(entregue, null);
        }
        String mascarado = guardavel(limpo);
        ChatDtos.Mensagem bloqueada = gravar(conversa, eu, mascarado, regras);
        return new ChatDtos.Envio(bloqueada, registrarTentativa(eu, bloqueada.id(), regras, mascarado));
    }

    @Transactional
    void marcarLidas(UUID conversa) {
        UUID eu = usuario.id();
        participar(conversa, eu);
        jdbc.update(
                """
                UPDATE mensagem SET lida_em = :agora
                 WHERE conversa_id = :conversa AND autor_id <> :eu AND lida_em IS NULL AND NOT bloqueada
                """,
                new MapSqlParameterSource("agora", agora())
                        .addValue("conversa", conversa)
                        .addValue("eu", eu));
    }

    private ChatDtos.Aviso registrarTentativa(UUID eu, UUID mensagem, List<String> regras, String mascarado) {
        jdbc.update(
                """
                INSERT INTO tentativa_contato (usuario_id, origem, mensagem_id, regras, trecho_mascarado, criado_em)
                VALUES (:eu, 'chat', :mensagem, CAST(:regras AS text[]), :trecho, :agora)
                """,
                new MapSqlParameterSource("eu", eu)
                        .addValue("mensagem", mensagem)
                        .addValue("regras", comoArray(regras))
                        .addValue("trecho", trecho(mascarado))
                        .addValue("agora", agora()));
        // todas as tentativas de chat da conta; zerar quando o admin reativar fica com a decisão do DOM-11
        Integer feitas = jdbc.queryForObject(
                "SELECT count(*) FROM tentativa_contato WHERE usuario_id = :eu AND origem = 'chat'",
                new MapSqlParameterSource("eu", eu),
                Integer.class);
        int tentativa = feitas == null ? 0 : feitas;
        int limite = configuracao.tentativasAntesAnalise();
        boolean emAnalise = tentativa > limite;
        if (emAnalise
                && jdbc.update(
                                "UPDATE usuario SET status = 'em_analise' WHERE id = :eu AND status = 'ativo'",
                                new MapSqlParameterSource("eu", eu))
                        == 1) {
            // sem o texto nem as regras: só o fato (o texto mascarado fica na tentativa, para a moderação)
            auditoria.registrarDoSistema(
                    "conta.em-analise",
                    "usuario",
                    eu,
                    Map.of("status", "ativo"),
                    Map.of("status", "em_analise", "motivo", "tentativas-de-contato"),
                    null);
        }
        return new ChatDtos.Aviso(tentativa, limite, emAnalise);
    }

    private ChatDtos.Mensagem gravar(UUID conversa, UUID autor, String texto, List<String> regras) {
        OffsetDateTime momento = agora();
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO mensagem (conversa_id, autor_id, texto, bloqueada, regras_violadas, criado_em)
                VALUES (:conversa, :autor, :texto, :bloqueada, CAST(:regras AS text[]), :agora) RETURNING id
                """,
                new MapSqlParameterSource("conversa", conversa)
                        .addValue("autor", autor)
                        .addValue("texto", texto)
                        .addValue("bloqueada", !regras.isEmpty())
                        .addValue("regras", comoArray(regras))
                        .addValue("agora", momento),
                UUID.class);
        return new ChatDtos.Mensagem(id, true, texto, !regras.isEmpty(), regras, false, momento.toInstant());
    }

    /** As conversas em que estou (como cliente ou como profissional); com {@code id}, só aquela. */
    private List<ChatDtos.Conversa> minhas(UUID eu, UUID id) {
        return jdbc.query(
                """
                SELECT c.id, c.profissional_id, c.ultima_mensagem_em, c.contato_liberado_em IS NOT NULL AS liberado,
                       p.usuario_id = :eu AS sou_profissional,
                       CASE WHEN p.usuario_id = :eu THEN cu.nome ELSE pu.nome END AS outro_nome,
                       (SELECT count(*) FROM mensagem m
                         WHERE m.conversa_id = c.id AND m.lida_em IS NULL AND NOT m.bloqueada
                           AND m.autor_id <> :eu) AS nao_lidas
                  FROM conversa c
                  JOIN profissional p ON p.id = c.profissional_id
                  JOIN usuario pu ON pu.id = p.usuario_id
                  JOIN usuario cu ON cu.id = c.cliente_id
                 WHERE (c.cliente_id = :eu OR p.usuario_id = :eu) AND (CAST(:id AS uuid) IS NULL OR c.id = :id)
                 ORDER BY c.ultima_mensagem_em DESC NULLS LAST, c.criado_em DESC LIMIT :limite
                """,
                new MapSqlParameterSource("eu", eu).addValue("id", id).addValue("limite", CONVERSAS_POR_VEZ),
                (linha, n) -> new ChatDtos.Conversa(
                        linha.getObject("id", UUID.class),
                        linha.getObject("profissional_id", UUID.class),
                        NomeDeExibicao.de(linha.getString("outro_nome")),
                        linha.getBoolean("sou_profissional"),
                        instante(linha, "ultima_mensagem_em"),
                        linha.getInt("nao_lidas"),
                        linha.getBoolean("liberado")));
    }

    /** Confere que estou na conversa (404 igual a inexistente) e diz se o contato já foi liberado. */
    private boolean participar(UUID conversa, UUID eu) {
        List<Boolean> liberado =
                jdbc.queryForList("""
                SELECT c.contato_liberado_em IS NOT NULL FROM conversa c JOIN profissional p ON p.id = c.profissional_id
                 WHERE c.id = :id AND (c.cliente_id = :eu OR p.usuario_id = :eu)
                """, new MapSqlParameterSource("id", conversa).addValue("eu", eu), Boolean.class);
        if (liberado.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        return Boolean.TRUE.equals(liberado.get(0));
    }

    private String travarConta(UUID eu) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM usuario WHERE id = :eu FOR NO KEY UPDATE",
                new MapSqlParameterSource("eu", eu),
                String.class);
        if (status.isEmpty()) {
            throw new RecursoNaoEncontradoException("Conta não encontrada.");
        }
        return status.get(0);
    }

    /** O texto bloqueado como pode ser guardado: mascarado e, se ainda sobrar contato, um texto genérico. */
    private static String guardavel(String texto) {
        String mascarado = CensuraDeContato.mascararContato(texto);
        boolean sobrouDado = CensuraDeContato.regrasVioladas(mascarado).stream().anyMatch(DADOS_DE_CONTATO::contains);
        return sobrouDado ? "[mensagem com contato]" : mascarado;
    }

    /** O começo do texto para a moderação, sem partir um caractere (emoji) no meio. */
    private static String trecho(String texto) {
        return texto.codePointCount(0, texto.length()) <= TRECHO_MAXIMO
                ? texto
                : texto.substring(0, texto.offsetByCodePoints(0, TRECHO_MAXIMO));
    }

    /** Conta em análise (ou suspensa) lê o que já tem, mas não abre conversa nem manda mensagem (DOM-05). */
    private static void exigirChatLiberado(String status) {
        if (!"ativo".equals(status)) {
            throw new AcaoProibidaException(
                    "conta-em-analise",
                    "Sua conta está em análise pela equipe COE. Enquanto isso, o chat fica fechado.");
        }
    }

    private static ChatDtos.Mensagem mensagem(ResultSet linha, UUID eu) throws SQLException {
        boolean minha = eu.equals(linha.getObject("autor_id", UUID.class));
        boolean bloqueada = linha.getBoolean("bloqueada");
        return new ChatDtos.Mensagem(
                linha.getObject("id", UUID.class),
                minha,
                linha.getString("texto"),
                bloqueada,
                bloqueada ? regras(linha.getArray("regras_violadas")) : List.of(),
                linha.getObject("lida_em") != null,
                instante(linha, "criado_em"));
    }

    private static List<String> regras(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    /** As regras são palavras fixas da censura (sem vírgula nem aspas): literal de array do Postgres. */
    private static String comoArray(List<String> regras) {
        return "{" + String.join(",", regras) + "}";
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
