package br.com.coe.servicos.usuario;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
import br.com.coe.servicos.compartilhado.log.Mascara;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Contas no painel do admin (DOM-11; RF52): achar pelo nome, ou pelo celular ou e-mail exatos; contato sempre
 * mascarado na resposta. Suspender (com motivo, todas as sessões revogadas) e reativar (de suspensa ou em análise pelo
 * chat). Conta da equipe e a própria conta não passam por aqui. A contagem de tentativas de contato não é zerada na
 * reativação (decisão pendente do produto).
 */
@Service
class AdminUsuariosService {

    static final int POR_PAGINA = 50;
    private static final String NAO_ENCONTRADA = "Conta não encontrada.";

    private final NamedParameterJdbcTemplate jdbc;
    private final RefreshTokenRepository sessoes;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    AdminUsuariosService(
            NamedParameterJdbcTemplate jdbc,
            RefreshTokenRepository sessoes,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.jdbc = jdbc;
        this.sessoes = sessoes;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    record Conta(
            UUID id,
            String nome,
            List<String> papeis,
            String status,
            String motivoStatus,
            String celular,
            String email,
            boolean celularConfirmado,
            boolean emailConfirmado,
            Instant criadoEm) {}

    record Pagina(List<Conta> itens, boolean temMais) {}

    record Suspensao(@NotBlank @Size(min = 10, max = 300) String motivo) {}

    private static final String COLUNAS = """
            SELECT u.id, u.nome, u.status, u.motivo_status, u.celular, u.email, u.criado_em,
                   u.celular_verificado_em IS NOT NULL AS celular_confirmado,
                   u.email_verificado_em IS NOT NULL AS email_confirmado,
                   ARRAY(SELECT p.papel FROM usuario_papel p WHERE p.usuario_id = u.id ORDER BY p.papel) AS papeis
              FROM usuario u
            """;

    @Transactional(readOnly = true)
    Pagina listar(String busca, String papel, String status, int pagina) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("limite", POR_PAGINA + 1)
                .addValue("deslocamento", (long) Math.max(pagina, 0) * POR_PAGINA);
        StringBuilder sql = new StringBuilder(COLUNAS).append(" WHERE true");
        if (busca != null && !busca.isBlank()) {
            String texto = busca.strip();
            if (texto.contains("@")) {
                sql.append(" AND u.email = :email");
                parametros.addValue("email", Contato.normalizarEmail(texto));
            } else if (Contato.celularDigitadoValido(texto)) {
                sql.append(" AND u.celular = :celular");
                parametros.addValue("celular", Contato.normalizarCelular(texto));
            } else {
                sql.append(" AND f_sem_acento(u.nome) ILIKE '%' || f_sem_acento(:nome) || '%' ESCAPE '\\'");
                parametros.addValue(
                        "nome", texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
            }
        }
        if (papel != null && !papel.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM usuario_papel p WHERE p.usuario_id = u.id AND p.papel = :papel)");
            parametros.addValue("papel", papel.strip());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND u.status = :status");
            parametros.addValue("status", status.strip());
        }
        sql.append(" ORDER BY u.criado_em DESC, u.id LIMIT :limite OFFSET :deslocamento");
        List<Conta> itens = jdbc.query(
                sql.toString(),
                parametros,
                (linha, n) -> conta(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getArray("papeis"),
                        linha.getString("status"),
                        linha.getString("motivo_status"),
                        linha.getString("celular"),
                        linha.getString("email"),
                        linha.getBoolean("celular_confirmado"),
                        linha.getBoolean("email_confirmado"),
                        linha.getObject("criado_em", OffsetDateTime.class)));
        boolean temMais = itens.size() > POR_PAGINA;
        return new Pagina(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional
    Conta suspender(UUID id, Suspensao pedido) {
        String antes = travar(id);
        if (!"ativo".equals(antes) && !"em_analise".equals(antes)) {
            throw new ConflitoException("status-invalido", "Só dá para suspender uma conta ativa ou em análise.", null);
        }
        jdbc.update(
                "UPDATE usuario SET status = 'suspenso', motivo_status = :motivo WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("motivo", pedido.motivo().strip()));
        sessoes.revogarDoUsuario(id, clock.instant(), "admin");
        auditar("conta.suspender", id, antes, "suspenso");
        return buscar(id);
    }

    @Transactional
    Conta reativar(UUID id) {
        String antes = travar(id);
        if (!"suspenso".equals(antes) && !"em_analise".equals(antes)) {
            throw new ConflitoException(
                    "status-invalido", "Só dá para reativar uma conta suspensa ou em análise.", null);
        }
        jdbc.update(
                "UPDATE usuario SET status = 'ativo', motivo_status = NULL WHERE id = :id",
                new MapSqlParameterSource("id", id));
        auditar("conta.reativar", id, antes, "ativo");
        return buscar(id);
    }

    /** Trava a conta (primeiro da ordem das travas); recusa a própria conta e conta da equipe. */
    private String travar(UUID id) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM usuario WHERE id = :id FOR NO KEY UPDATE",
                new MapSqlParameterSource("id", id),
                String.class);
        if (status.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        if (id.equals(usuario.id())) {
            throw new AcaoProibidaException("acao-propria", "Você não pode mudar a situação da sua própria conta.");
        }
        Boolean daEquipe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario_papel WHERE usuario_id = :id AND papel = 'ADMIN')",
                new MapSqlParameterSource("id", id),
                Boolean.class);
        if (Boolean.TRUE.equals(daEquipe)) {
            throw new AcaoProibidaException("conta-da-equipe", "Conta da equipe se muda pela Equipe, não por aqui.");
        }
        return status.get(0);
    }

    private Conta buscar(UUID id) {
        return jdbc
                .query(
                        COLUNAS + " WHERE u.id = :id",
                        new MapSqlParameterSource("id", id),
                        (linha, n) -> conta(
                                linha.getObject("id", UUID.class),
                                linha.getString("nome"),
                                linha.getArray("papeis"),
                                linha.getString("status"),
                                linha.getString("motivo_status"),
                                linha.getString("celular"),
                                linha.getString("email"),
                                linha.getBoolean("celular_confirmado"),
                                linha.getBoolean("email_confirmado"),
                                linha.getObject("criado_em", OffsetDateTime.class)))
                .stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(NAO_ENCONTRADA));
    }

    private void auditar(String acao, UUID id, String antes, String depois) {
        auditoria.registrar(
                usuario.id(), "ADMIN", acao, "usuario", id, Map.of("status", antes), Map.of("status", depois), null);
    }

    private static Conta conta(
            UUID id,
            String nome,
            Array papeis,
            String status,
            String motivo,
            String celular,
            String email,
            boolean celularConfirmado,
            boolean emailConfirmado,
            OffsetDateTime criadoEm)
            throws SQLException {
        return new Conta(
                id,
                nome,
                Arrays.stream((Object[]) papeis.getArray())
                        .map(Object::toString)
                        .toList(),
                status,
                motivo,
                celular == null ? null : Mascara.celular(celular),
                email == null ? null : Mascara.email(email),
                celularConfirmado,
                emailConfirmado,
                criadoEm.toInstant());
    }
}
