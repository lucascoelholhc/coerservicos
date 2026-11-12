package br.com.coe.servicos.pagamento;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Repasses e alertas do financeiro no painel do admin (PA02): lista de repasses por status, nova tentativa manual de
 * um repasse que falhou (auditada) e os alertas abertos, que o admin marca como resolvidos. Sem chave Pix nem contato:
 * o nome do profissional sai como "Primeiro S.".
 */
@Service
class AdminRepassesService {

    private static final int POR_PAGINA = 50;
    private static final int PAGINA_MAXIMA = 1_000;
    private static final Set<String> STATUS = Set.of("pendente", "enviado", "confirmado", "falhou");

    record Repasse(
            UUID id,
            UUID diariaId,
            UUID profissionalId,
            String profissional,
            Dinheiro valor,
            String status,
            String erro,
            int tentativas,
            boolean pixConferido,
            Instant criadoEm,
            Instant enviadoEm,
            Instant confirmadoEm) {}

    record PaginaDeRepasses(List<Repasse> itens, boolean temMais) {}

    record Alerta(
            UUID id,
            String tipo,
            UUID referencia,
            Map<String, Object> detalhe,
            Instant criadoEm,
            Instant resolvidoEm) {}

    record PaginaDeAlertas(List<Alerta> itens, boolean temMais) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final JsonMapper json;
    private final Clock clock;

    AdminRepassesService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            JsonMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PaginaDeRepasses repasses(String status, int pagina) {
        if (status != null && !STATUS.contains(status)) {
            throw new RegraDeNegocioException("filtro-invalido", "Esse status de repasse não existe.");
        }
        List<Repasse> itens = jdbc.query(
                """
                SELECT r.id, rd.diaria_id, r.profissional_id, u.nome, r.valor, r.status, r.erro, r.tentativas,
                       p.pix_conferido_em IS NOT NULL AS conferido, r.criado_em, r.enviado_em, r.confirmado_em
                  FROM repasse r
                  JOIN repasse_diaria rd ON rd.repasse_id = r.id
                  JOIN profissional p ON p.id = r.profissional_id
                  JOIN usuario u ON u.id = p.usuario_id
                 WHERE (CAST(:status AS text) IS NULL OR r.status = :status)
                 ORDER BY r.criado_em DESC, r.id
                 LIMIT :limite OFFSET :pular
                """,
                new MapSqlParameterSource("status", status)
                        .addValue("limite", POR_PAGINA + 1)
                        .addValue("pular", (long) Math.clamp(pagina, 0, PAGINA_MAXIMA) * POR_PAGINA),
                (rs, n) -> new Repasse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("diaria_id", UUID.class),
                        rs.getObject("profissional_id", UUID.class),
                        nomeDeExibicao(rs.getString("nome")),
                        new Dinheiro(rs.getBigDecimal("valor")),
                        rs.getString("status"),
                        rs.getString("erro"),
                        rs.getInt("tentativas"),
                        rs.getBoolean("conferido"),
                        instante(rs.getObject("criado_em", OffsetDateTime.class)),
                        instante(rs.getObject("enviado_em", OffsetDateTime.class)),
                        instante(rs.getObject("confirmado_em", OffsetDateTime.class))));
        boolean temMais = itens.size() > POR_PAGINA;
        return new PaginaDeRepasses(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    /** Repasse que falhou volta para pendente: o envio manda de novo com a chave vigente (que o admin conferiu). */
    @Transactional
    void tentarDeNovo(UUID id) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM repasse WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id),
                String.class);
        if (status.isEmpty()) {
            throw new RecursoNaoEncontradoException("Repasse não encontrado.");
        }
        if (!"falhou".equals(status.get(0))) {
            throw new ConflitoException(
                    "repasse-indisponivel", "Só dá para mandar de novo um repasse que falhou.", null);
        }
        jdbc.update("UPDATE repasse SET status = 'pendente' WHERE id = :id", new MapSqlParameterSource("id", id));
        auditoria.registrar(
                usuario.id(),
                "ADMIN",
                "repasse.tentar-de-novo",
                "repasse",
                id,
                Map.of("status", "falhou"),
                Map.of("status", "pendente"),
                null);
    }

    @Transactional(readOnly = true)
    PaginaDeAlertas alertas(boolean abertos, int pagina) {
        List<Alerta> itens = jdbc.query(
                """
                SELECT id, tipo, referencia, detalhe::text AS detalhe, criado_em, resolvido_em
                  FROM alerta_financeiro
                 WHERE (NOT :abertos OR resolvido_em IS NULL)
                 ORDER BY criado_em DESC, id
                 LIMIT :limite OFFSET :pular
                """,
                new MapSqlParameterSource("abertos", abertos)
                        .addValue("limite", POR_PAGINA + 1)
                        .addValue("pular", (long) Math.clamp(pagina, 0, PAGINA_MAXIMA) * POR_PAGINA),
                (rs, n) -> new Alerta(
                        rs.getObject("id", UUID.class),
                        rs.getString("tipo"),
                        rs.getObject("referencia", UUID.class),
                        json.readValue(rs.getString("detalhe"), new TypeReference<Map<String, Object>>() {}),
                        instante(rs.getObject("criado_em", OffsetDateTime.class)),
                        instante(rs.getObject("resolvido_em", OffsetDateTime.class))));
        boolean temMais = itens.size() > POR_PAGINA;
        return new PaginaDeAlertas(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional
    void resolverAlerta(UUID id) {
        List<OffsetDateTime> resolvido = jdbc.query(
                "SELECT resolvido_em FROM alerta_financeiro WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id),
                (rs, n) -> rs.getObject("resolvido_em", OffsetDateTime.class));
        if (resolvido.isEmpty()) {
            throw new RecursoNaoEncontradoException("Alerta não encontrado.");
        }
        if (resolvido.get(0) != null) {
            throw new ConflitoException("alerta-resolvido", "Esse alerta já foi resolvido.", null);
        }
        UUID admin = usuario.id();
        jdbc.update(
                "UPDATE alerta_financeiro SET resolvido_em = :agora, resolvido_por = :admin WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("admin", admin)
                        .addValue("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        auditoria.registrar(
                admin, "ADMIN", "alerta.resolver", "alerta_financeiro", id, Map.of(), Map.of("resolvido", true), null);
    }

    /** "Valdir Schmitt" vira "Valdir S." (o painel mostra só o necessário para achar o profissional). */
    static String nomeDeExibicao(String nome) {
        String[] partes = nome == null ? new String[0] : nome.strip().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) {
            return "";
        }
        return partes.length == 1 ? partes[0] : partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
    }

    private static Instant instante(OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }
}
