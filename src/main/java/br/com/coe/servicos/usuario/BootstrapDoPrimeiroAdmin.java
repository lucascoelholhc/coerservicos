package br.com.coe.servicos.usuario;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;

/**
 * Primeiro admin em produção (DOM-11, lacuna de 26/10): comando de uso único, ligado só com
 * {@code coe.bootstrap-admin.ativo=true} (a tarefa do ECS da AWS-10). Nome, celular e e-mail vêm das variáveis
 * {@code COE_BOOTSTRAP_ADMIN_NOME}, {@code _CELULAR} e {@code _EMAIL}; a senha nunca vem por variável: a conta nasce com
 * uma senha aleatória que ninguém sabe e recebe o link de "redefinir senha" no e-mail. Celular e e-mail entram como
 * confirmados (vêm de quem opera a infraestrutura) e o MFA já nasce ligado. Recusa (sem mudar nada) se já houver
 * qualquer ADMIN ou se o contato já for de outra conta; nunca roda no perfil local (lá o seed cria o admin). Não é um
 * {@code ApplicationRunner} porque esses ficam só no perfil local (ArchUnit, DB-11).
 */
@Component
@ConditionalOnProperty(name = "coe.bootstrap-admin.ativo", havingValue = "true")
class BootstrapDoPrimeiroAdmin {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapDoPrimeiroAdmin.class);
    static final Duration VALIDADE_DO_LINK = Duration.ofHours(24);
    private static final ServicoDeTokens.Email EMAIL =
            new ServicoDeTokens.Email("/redefinir-senha", "Sua conta na equipe da COE", link -> """
                    Olá!

                    Sua conta de administrador da COE foi criada. Para criar a sua senha, abra o link abaixo
                    (ele vale por 24 horas):

                    %s

                    Depois, entre com o seu celular ou e-mail, a senha nova e o código que chega por SMS.
                    """.formatted(link));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder codificador;
    private final ServicoDeTokens tokens;
    private final RegistroDeAuditoria auditoria;
    private final TransactionTemplate transacao;
    private final ConfigurableApplicationContext contexto;
    private final String nome;
    private final String celular;
    private final String email;
    private final boolean sairAoTerminar;
    private volatile boolean contatoEmUso;

    BootstrapDoPrimeiroAdmin(
            Environment ambiente,
            JdbcTemplate jdbc,
            PasswordEncoder codificador,
            ServicoDeTokens tokens,
            RegistroDeAuditoria auditoria,
            PlatformTransactionManager transacoes,
            ConfigurableApplicationContext contexto,
            @Value("${coe.bootstrap-admin.nome:}") String nome,
            @Value("${coe.bootstrap-admin.celular:}") String celular,
            @Value("${coe.bootstrap-admin.email:}") String email,
            @Value("${coe.bootstrap-admin.sair-ao-terminar:true}") boolean sairAoTerminar) {
        if (Arrays.asList(ambiente.getActiveProfiles()).contains("local")) {
            throw new IllegalStateException(
                    "O bootstrap do primeiro admin nunca roda no perfil local (o seed local já cria o admin).");
        }
        if (nome == null || nome.isBlank() || nome.strip().length() > 120) {
            throw new IllegalStateException("COE_BOOTSTRAP_ADMIN_NOME ausente ou longo demais.");
        }
        String celularNormalizado = celular == null ? "" : Contato.normalizarCelular(celular);
        if (!Contato.celularValido(celularNormalizado)) {
            throw new IllegalStateException("COE_BOOTSTRAP_ADMIN_CELULAR ausente ou inválido (11 dígitos com DDD).");
        }
        String emailNormalizado = email == null ? "" : Contato.normalizarEmail(email);
        if (!Contato.emailValido(emailNormalizado)) {
            throw new IllegalStateException("COE_BOOTSTRAP_ADMIN_EMAIL ausente ou inválido.");
        }
        this.jdbc = jdbc;
        this.codificador = codificador;
        this.tokens = tokens;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transacoes);
        this.contexto = contexto;
        this.nome = nome.strip();
        this.celular = celularNormalizado;
        this.email = emailNormalizado;
        this.sairAoTerminar = sairAoTerminar;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        boolean criou = executar();
        LOG.info("Bootstrap do primeiro admin: {}", criou ? "conta criada, link na fila" : "nada feito");
        if (sairAoTerminar && contexto != null) {
            esperarAFila();
            // código 1 quando não criou porque o contato é de outra conta: a tarefa não pode "passar" sem ADMIN
            int codigo = !criou && contatoEmUso ? 1 : 0;
            SpringApplication.exit(contexto, () -> codigo);
        }
    }

    /** Cria o primeiro ADMIN; devolve falso (sem mudar nada) se já houver ADMIN ou o contato for de outra conta. */
    boolean executar() {
        Boolean criou = transacao.execute(status -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('bootstrap-admin'))");
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM usuario_papel WHERE papel = 'ADMIN')", Boolean.class))) {
                LOG.info("Já existe ADMIN: o bootstrap não faz nada.");
                return false;
            }
            List<UUID> emUso = jdbc.queryForList(
                    "SELECT id FROM usuario WHERE celular = ? OR email = ?", UUID.class, celular, email);
            if (!emUso.isEmpty()) {
                contatoEmUso = true;
                LOG.warn("Contato do bootstrap já é de outra conta: nada feito (resolva antes de rodar de novo).");
                return false;
            }
            byte[] aleatoria = new byte[32];
            new SecureRandom().nextBytes(aleatoria);
            UUID id = jdbc.queryForObject(
                    """
                    INSERT INTO usuario (nome, celular, email, senha_hash, status, mfa_sms_ativo,
                                         celular_verificado_em, email_verificado_em)
                    VALUES (?, ?, ?, ?, 'ativo', true, now(), now()) RETURNING id
                    """,
                    UUID.class,
                    nome,
                    celular,
                    email,
                    codificador.encode(Base64.getEncoder().encodeToString(aleatoria)));
            jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'ADMIN')", id);
            auditoria.registrarDoSistema("admin.bootstrap", "usuario", id, Map.of(), Map.of("papel", "ADMIN"), null);
            if (!tokens.enviarLink(id, email, FinalidadeToken.RECUPERAR_SENHA, VALIDADE_DO_LINK, EMAIL)) {
                LOG.warn("Link de criar a senha do primeiro admin não enviado (limite): use \"Esqueci a senha\".");
            }
            return true;
        });
        return Boolean.TRUE.equals(criou);
    }

    /** O link sai por uma fila depois do commit: espera ela esvaziar (até 30 s) antes de encerrar o processo. */
    private void esperarAFila() {
        br.com.coe.servicos.compartilhado.mensageria.DespachoDeMensagens despacho = contexto.getBeanProvider(
                        br.com.coe.servicos.compartilhado.mensageria.DespachoDeMensagens.class)
                .getIfAvailable();
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (despacho != null && despacho.pendentes() > 0 && System.nanoTime() < limite) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
