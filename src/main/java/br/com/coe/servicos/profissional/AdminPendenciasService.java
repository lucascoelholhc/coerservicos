package br.com.coe.servicos.profissional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Pendências de perfis já aprovados (RN15a, RN57, 11/11): foto nova (oculta até ser aprovada) e documento novo
 * (pendente). O perfil segue na busca com o que já foi aprovado; o admin aprova ou recusa cada item, auditado (sem o
 * motivo da recusa, que pode ter texto livre).
 */
@Service
class AdminPendenciasService {

    private static final int LIMITE = 50;

    record Foto(UUID id, String servico, String legenda) {}

    record Documento(UUID id, String tipo) {}

    record Pendencia(UUID profissionalId, String nome, List<Foto> fotos, List<Documento> documentos) {}

    record Lista(List<Pendencia> itens) {}

    record Recusa(@NotBlank @Size(min = 5, max = 160) String motivo) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    AdminPendenciasService(
            NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, RegistroDeAuditoria auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Lista listar() {
        Map<UUID, Pendencia> porPerfil = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id AS perfil, u.nome, f.id AS foto, s.nome AS servico, f.legenda
                  FROM foto_portfolio f
                  JOIN profissional p ON p.id = f.profissional_id
                  JOIN usuario u ON u.id = p.usuario_id
                  LEFT JOIN servico s ON s.id = f.servico_id
                 WHERE f.pendente AND p.status IN ('ativo', 'pausado')
                 ORDER BY f.criado_em, f.id
                 LIMIT :limite
                """, new MapSqlParameterSource("limite", LIMITE), rs -> {
            pendencia(porPerfil, rs.getObject("perfil", UUID.class), rs.getString("nome"))
                    .fotos()
                    .add(new Foto(rs.getObject("foto", UUID.class), rs.getString("servico"), rs.getString("legenda")));
        });
        jdbc.query("""
                SELECT p.id AS perfil, u.nome, d.id AS documento, d.tipo
                  FROM documento_verificacao d
                  JOIN profissional p ON p.id = d.profissional_id
                  JOIN usuario u ON u.id = p.usuario_id
                 WHERE d.status = 'pendente' AND p.status IN ('ativo', 'pausado')
                 ORDER BY d.criado_em, d.id
                 LIMIT :limite
                """, new MapSqlParameterSource("limite", LIMITE), rs -> {
            pendencia(porPerfil, rs.getObject("perfil", UUID.class), rs.getString("nome"))
                    .documentos()
                    .add(new Documento(rs.getObject("documento", UUID.class), rs.getString("tipo")));
        });
        return new Lista(List.copyOf(porPerfil.values()));
    }

    @Transactional
    void aprovarFoto(UUID perfil, UUID foto) {
        decidirFoto(perfil, foto, true, "profissional.foto-aprovada");
    }

    @Transactional
    void recusarFoto(UUID perfil, UUID foto) {
        decidirFoto(perfil, foto, false, "profissional.foto-recusada");
    }

    @Transactional
    void aprovarDocumento(UUID perfil, UUID documento) {
        decidirDocumento(perfil, documento, "aprovado", null, "profissional.documento-aprovado");
    }

    @Transactional
    void recusarDocumento(UUID perfil, UUID documento, Recusa recusa) {
        CensuraDeContato.exigirSemContato(recusa.motivo(), "motivo");
        decidirDocumento(perfil, documento, "recusado", recusa.motivo().strip(), "profissional.documento-recusado");
    }

    private void decidirFoto(UUID perfil, UUID foto, boolean aprovada, String acao) {
        int mudou = jdbc.update(
                """
                UPDATE foto_portfolio SET pendente = false, visivel = :aprovada
                 WHERE id = :foto AND profissional_id = :perfil AND pendente
                """,
                new MapSqlParameterSource("foto", foto)
                        .addValue("perfil", perfil)
                        .addValue("aprovada", aprovada));
        if (mudou == 0) {
            throw new RecursoNaoEncontradoException("Foto pendente não encontrada.");
        }
        auditar(acao, perfil, Map.of("foto", foto.toString()));
    }

    private void decidirDocumento(UUID perfil, UUID documento, String status, String motivo, String acao) {
        List<String> tipo = jdbc.queryForList(
                """
                UPDATE documento_verificacao d
                   SET status = :status, analisado_por = :admin, analisado_em = :agora, motivo_recusa = :motivo
                  FROM profissional p
                 WHERE d.id = :documento AND d.profissional_id = :perfil AND d.status = 'pendente'
                   AND p.id = d.profissional_id AND p.status IN ('ativo', 'pausado')
                RETURNING d.tipo
                """,
                new MapSqlParameterSource("documento", documento)
                        .addValue("perfil", perfil)
                        .addValue("status", status)
                        .addValue("motivo", motivo)
                        .addValue("admin", usuario.id())
                        .addValue("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)),
                String.class);
        if (tipo.isEmpty()) {
            throw new RecursoNaoEncontradoException("Documento pendente não encontrado.");
        }
        auditar(acao, perfil, Map.of("tipo", tipo.get(0)));
    }

    private void auditar(String acao, UUID perfil, Map<String, ?> depois) {
        auditoria.registrar(usuario.id(), "ADMIN", acao, "profissional", perfil, null, depois, null);
    }

    private static Pendencia pendencia(Map<UUID, Pendencia> porPerfil, UUID perfil, String nome) {
        return porPerfil.computeIfAbsent(perfil, id -> new Pendencia(id, nome, new ArrayList<>(), new ArrayList<>()));
    }
}
