package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.log.Mascara;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Equipe de administradores (DOM-11, lacuna de 26/10). Toda ação (convidar, aceitar, remover) pede código SMS na hora
 * (finalidade {@code acao_admin}), conferido fora da transação da mudança (a tentativa errada fica gravada). As
 * mudanças travam todas as linhas de ADMIN ({@code usuario_papel FOR UPDATE}, em ordem): duas remoções ao mesmo tempo
 * passam uma de cada vez e o sistema nunca fica sem ADMIN. Quem age precisa continuar ADMIN no banco (o token pode ser
 * de antes da remoção). Quem ganha ou perde o papel tem as sessões revogadas e é avisado por SMS (e e-mail, se
 * confirmado). Auditoria só com ids.
 */
@Service
class EquipeService {

    private static final Logger LOG = LoggerFactory.getLogger(EquipeService.class);
    static final Duration VALIDADE_DO_CONVITE = Duration.ofDays(7);

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final ContaDeQuemChama contaDeQuemChama;
    private final ServicoDeCodigoSms codigos;
    private final RefreshTokenRepository sessoes;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;
    private final Clock clock;

    EquipeService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioRepository usuarios,
            ContaDeQuemChama contaDeQuemChama,
            ServicoDeCodigoSms codigos,
            RefreshTokenRepository sessoes,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            ApplicationEventPublisher eventos,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.contaDeQuemChama = contaDeQuemChama;
        this.codigos = codigos;
        this.sessoes = sessoes;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    record Admin(UUID id, String nome, String celular, String email, boolean eu) {}

    record Convite(UUID id, UUID convidadoId, String nome, Instant expiraEm) {}

    record Equipe(List<Admin> admins, List<Convite> convites) {}

    record MeuConvite(UUID id, String convidadoPor, Instant expiraEm) {}

    record NovoConvite(
            @NotBlank @Size(max = 254) String login,
            @NotBlank @Size(max = 10) String codigo) {}

    record Codigo(@NotBlank @Size(max = 10) String codigo) {}

    Equipe equipe() {
        UUID eu = usuario.id();
        List<Admin> admins = jdbc.query(
                """
                SELECT u.id, u.nome, u.celular, u.email FROM usuario u
                  JOIN usuario_papel p ON p.usuario_id = u.id AND p.papel = 'ADMIN'
                 ORDER BY u.nome, u.id
                """,
                new MapSqlParameterSource(),
                (linha, n) -> new Admin(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("celular") == null ? null : Mascara.celular(linha.getString("celular")),
                        linha.getString("email") == null ? null : Mascara.email(linha.getString("email")),
                        eu.equals(linha.getObject("id", UUID.class))));
        List<Convite> convites = jdbc.query(
                """
                SELECT c.id, c.convidado_id, u.nome, c.expira_em FROM convite_admin c
                  JOIN usuario u ON u.id = c.convidado_id
                 WHERE c.aceito_em IS NULL AND c.cancelado_em IS NULL AND c.expira_em > :agora
                 ORDER BY c.criado_em
                """,
                new MapSqlParameterSource("agora", agora()),
                (linha, n) -> new Convite(
                        linha.getObject("id", UUID.class),
                        linha.getObject("convidado_id", UUID.class),
                        linha.getString("nome"),
                        linha.getObject("expira_em", OffsetDateTime.class).toInstant()));
        return new Equipe(admins, convites);
    }

    /** Código da ação para o celular confirmado de quem pede (admin agindo ou convidado aceitando). */
    void pedirCodigo(String ip) {
        Usuario conta = contaDeQuemChama.carregarAtiva();
        if (!conta.isCelularConfirmado()) {
            throw new AcaoProibidaException("celular-nao-confirmado", "Confirme o seu celular antes de continuar.");
        }
        if (!codigos.enviar(conta.getId(), conta.getCelular(), FinalidadeSms.ACAO_ADMIN, ip)) {
            throw new MuitasTentativasException(
                    "Você pediu muitos códigos. Aguarde um pouco e tente de novo.",
                    codigos.esperaParaEnviar(conta.getCelular(), FinalidadeSms.ACAO_ADMIN));
        }
    }

    void convidar(NovoConvite pedido) {
        UUID eu = usuario.id();
        exigirCodigo(eu, pedido.codigo());
        Usuario convidado = usuarios.buscarPeloLogin(pedido.login().strip())
                .filter(conta -> "ativo".equals(conta.getStatus()))
                .filter(Usuario::isCelularConfirmado)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Não achamos uma conta ativa com celular confirmado para esse login."));
        transacao.executeWithoutResult(status -> {
            List<UUID> admins = travarAdmins();
            exigirDaEquipe(eu, admins);
            if (admins.contains(convidado.getId())) {
                throw new ConflitoException("ja-e-admin", "Essa pessoa já faz parte da equipe.", "login");
            }
            OffsetDateTime agora = agora();
            jdbc.update("""
                    UPDATE convite_admin SET cancelado_em = :agora
                     WHERE convidado_id = :convidado AND aceito_em IS NULL AND cancelado_em IS NULL
                    """, new MapSqlParameterSource("convidado", convidado.getId()).addValue("agora", agora));
            UUID convite = jdbc.queryForObject(
                    """
                    INSERT INTO convite_admin (convidado_id, convidado_por, criado_em, expira_em)
                    VALUES (:convidado, :eu, :agora, :expira) RETURNING id
                    """,
                    new MapSqlParameterSource("convidado", convidado.getId())
                            .addValue("eu", eu)
                            .addValue("agora", agora)
                            .addValue("expira", agora.plus(VALIDADE_DO_CONVITE)),
                    UUID.class);
            auditoria.registrar(
                    eu,
                    "ADMIN",
                    "admin.convidar",
                    "usuario",
                    convidado.getId(),
                    Map.of(),
                    Map.of("convite", convite),
                    null);
            avisar(
                    convidado,
                    "COE: você foi convidado para a equipe da COE. Entre no app, em Minha conta, para aceitar.");
        });
    }

    Optional<MeuConvite> meuConvite() {
        return jdbc
                .query(
                        """
                        SELECT c.id, u.nome, c.expira_em FROM convite_admin c
                          JOIN usuario u ON u.id = c.convidado_por
                         WHERE c.convidado_id = :eu AND c.aceito_em IS NULL AND c.cancelado_em IS NULL
                           AND c.expira_em > :agora
                        """,
                        new MapSqlParameterSource("eu", usuario.id()).addValue("agora", agora()),
                        (linha, n) -> new MeuConvite(
                                linha.getObject("id", UUID.class),
                                br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao.de(linha.getString("nome")),
                                linha.getObject("expira_em", OffsetDateTime.class)
                                        .toInstant()))
                .stream()
                .findFirst();
    }

    /** Aceitar: vira ADMIN com o código SMS no login ligado (MFA obrigatório) e entra de novo. */
    void aceitar(Codigo pedido) {
        UUID eu = usuario.id();
        exigirCodigo(eu, pedido.codigo());
        transacao.executeWithoutResult(status -> {
            List<UUID> admins = travarAdmins();
            Boolean podeSer = jdbc.queryForObject(
                    "SELECT status = 'ativo' AND celular_verificado_em IS NOT NULL FROM usuario"
                            + " WHERE id = :eu FOR NO KEY UPDATE",
                    new MapSqlParameterSource("eu", eu),
                    Boolean.class);
            if (!Boolean.TRUE.equals(podeSer)) {
                throw new ConflitoException(
                        "conta-indisponivel", "Sua conta precisa estar ativa e com o celular confirmado.", null);
            }
            // convite de quem já saiu da equipe não vale
            List<UUID> convites = jdbc.queryForList(
                    """
                    SELECT id FROM convite_admin
                     WHERE convidado_id = :eu AND aceito_em IS NULL AND cancelado_em IS NULL AND expira_em > :agora
                       AND convidado_por IN (:admins)
                     FOR UPDATE
                    """,
                    new MapSqlParameterSource("eu", eu)
                            .addValue("agora", agora())
                            .addValue("admins", admins.isEmpty() ? List.of(new UUID(0, 0)) : admins),
                    UUID.class);
            if (convites.isEmpty()) {
                throw new RecursoNaoEncontradoException("Convite não encontrado ou vencido.");
            }
            OffsetDateTime agora = agora();
            jdbc.update(
                    "UPDATE convite_admin SET aceito_em = :agora WHERE id = :id",
                    new MapSqlParameterSource("id", convites.get(0)).addValue("agora", agora));
            jdbc.update(
                    "INSERT INTO usuario_papel (usuario_id, papel) VALUES (:eu, 'ADMIN') ON CONFLICT DO NOTHING",
                    new MapSqlParameterSource("eu", eu));
            jdbc.update("UPDATE usuario SET mfa_sms_ativo = true WHERE id = :eu", new MapSqlParameterSource("eu", eu));
            sessoes.revogarDoUsuario(eu, agora.toInstant(), "admin");
            auditoria.registrar(
                    eu, "ADMIN", "admin.aceitar", "usuario", eu, Map.of(), Map.of("convite", convites.get(0)), null);
        });
        LOG.info("Convite da equipe aceito: {}", eu);
    }

    void remover(UUID alvo, Codigo pedido) {
        removerComo(usuario.id(), alvo, pedido.codigo());
    }

    /** O mesmo que {@link #remover}, com quem age explícito (o teste do último admin roda numa transação própria). */
    void removerComo(UUID eu, UUID alvo, String codigo) {
        exigirCodigo(eu, codigo);
        transacao.executeWithoutResult(status -> {
            List<UUID> admins = travarAdmins();
            exigirDaEquipe(eu, admins);
            if (!admins.contains(alvo)) {
                throw new RecursoNaoEncontradoException("Essa pessoa não faz parte da equipe.");
            }
            if (admins.size() <= 1) {
                throw new ConflitoException(
                        "ultimo-admin", "Não dá para tirar o último administrador da equipe.", null);
            }
            jdbc.update(
                    "DELETE FROM usuario_papel WHERE usuario_id = :alvo AND papel = 'ADMIN'",
                    new MapSqlParameterSource("alvo", alvo));
            sessoes.revogarDoUsuario(alvo, clock.instant(), "admin");
            // convite pendente de quem saiu deixa de valer
            jdbc.update("""
                    UPDATE convite_admin SET cancelado_em = :agora
                     WHERE convidado_por = :alvo AND aceito_em IS NULL AND cancelado_em IS NULL
                    """, new MapSqlParameterSource("alvo", alvo).addValue("agora", agora()));
            auditoria.registrar(
                    eu, "ADMIN", "admin.remover", "usuario", alvo, Map.of("papel", "ADMIN"), Map.of(), null);
            usuarios.findById(alvo)
                    .ifPresent(conta ->
                            avisar(conta, "COE: você não faz mais parte da equipe da COE. Entre de novo no app."));
        });
    }

    /** Todas as linhas de ADMIN travadas, sempre na mesma ordem (sem deadlock entre duas ações da equipe). */
    private List<UUID> travarAdmins() {
        return jdbc.queryForList(
                "SELECT usuario_id FROM usuario_papel WHERE papel = 'ADMIN' ORDER BY usuario_id FOR UPDATE",
                new MapSqlParameterSource(),
                UUID.class);
    }

    private static void exigirDaEquipe(UUID eu, List<UUID> admins) {
        if (!admins.contains(eu)) {
            throw new AcaoProibidaException("fora-da-equipe", "Você não faz mais parte da equipe.");
        }
    }

    /** Fora da transação da mudança: a tentativa errada conta mesmo quando a ação é recusada. */
    private void exigirCodigo(UUID conta, String codigo) {
        if (codigos.bloqueado(conta)) {
            throw new MuitasTentativasException(
                    "Muitos códigos errados. Aguarde um pouco e tente de novo.", codigos.esperaDoBloqueio(conta));
        }
        if (codigos.conferirDaConta(conta, FinalidadeSms.ACAO_ADMIN, codigo).isEmpty()) {
            throw new RegraDeNegocioException("codigo-invalido", "Código incorreto ou vencido. Peça um novo.");
        }
    }

    private void avisar(Usuario conta, String texto) {
        if (conta.getCelular() != null && conta.isCelularConfirmado()) {
            eventos.publishEvent(MensagemPronta.sms(conta.getCelular(), texto));
        }
        if (conta.getEmail() != null && conta.isEmailConfirmado()) {
            eventos.publishEvent(MensagemPronta.email(conta.getEmail(), "Equipe da COE", texto));
        }
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
