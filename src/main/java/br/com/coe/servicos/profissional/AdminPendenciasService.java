package br.com.coe.servicos.profissional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Fotos novas de perfis já aprovados (RN15a, RN57, 11/11): nascem ocultas e esperam o admin aprovar (aparece na busca)
 * ou recusar (continua oculta). Documento novo não passa por aqui: o cadastro inteiro volta para a fila de verificação
 * (DOM-03). O admin nunca decide o próprio perfil; a auditoria leva só o id da foto.
 */
@Service
class AdminPendenciasService {

    private static final int LIMITE = 50;

    record Foto(UUID id, String servico, String legenda) {}

    record Pendencia(UUID profissionalId, String nome, List<Foto> fotos) {}

    record Lista(List<Pendencia> itens) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;

    AdminPendenciasService(NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, RegistroDeAuditoria auditoria) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.auditoria = auditoria;
    }

    /** As fotos pendentes mais antigas primeiro (até 50 por vez; decidir libera a vez das próximas). */
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
            String nome = rs.getString("nome");
            porPerfil
                    .computeIfAbsent(
                            rs.getObject("perfil", UUID.class), id -> new Pendencia(id, nome, new ArrayList<>()))
                    .fotos()
                    .add(new Foto(rs.getObject("foto", UUID.class), rs.getString("servico"), rs.getString("legenda")));
        });
        return new Lista(List.copyOf(porPerfil.values()));
    }

    @Transactional
    void aprovarFoto(UUID perfil, UUID foto) {
        decidir(perfil, foto, true, "profissional.foto-aprovada");
    }

    @Transactional
    void recusarFoto(UUID perfil, UUID foto) {
        decidir(perfil, foto, false, "profissional.foto-recusada");
    }

    private void decidir(UUID perfil, UUID foto, boolean aprovada, String acao) {
        List<UUID> dono = jdbc.queryForList(
                "SELECT usuario_id FROM profissional WHERE id = :perfil AND status IN ('ativo', 'pausado') FOR UPDATE",
                new MapSqlParameterSource("perfil", perfil),
                UUID.class);
        if (dono.isEmpty()) {
            throw new RecursoNaoEncontradoException("Foto pendente não encontrada.");
        }
        if (dono.get(0).equals(usuario.id())) {
            throw new AcaoProibidaException(
                    "verificacao-propria", "Quem decide o próprio perfil é outra pessoa da equipe.");
        }
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
        auditoria.registrar(
                usuario.id(), "ADMIN", acao, "profissional", perfil, null, Map.of("foto", foto.toString()), null);
    }
}
