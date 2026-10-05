package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessões por aparelho (PA03): refresh de 30 dias, renovado a cada uso; reuso depois de 30 s
 * revoga a família (sinal de roubo). As recusas DEVOLVEM um resultado em vez de lançar exceção
 * dentro da transação, para a revogação ser gravada (exceção desfaria tudo no rollback).
 */
@Service
class ServicoDeSessao {

    static final Duration VALIDADE = Duration.ofDays(30);
    static final Duration TOLERANCIA_REUSO = Duration.ofSeconds(30);

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeSessao.class);

    record Renovada(UsuarioResumo usuario, String refresh) {}

    private final RefreshTokenRepository tokens;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ServicoDeSessao(
            RefreshTokenRepository tokens,
            UsuarioRepository usuarios,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    /** Login aceito: regrava o hash (se veio um novo), marca o login e abre a sessão do aparelho. */
    String abrir(UUID usuarioId, String novoHashDaSenha, String ip, String userAgent) {
        String refresh = TokenDeRenovacao.gerar();
        Instant agora = agora();
        transacao.executeWithoutResult(status -> {
            if (novoHashDaSenha != null) {
                usuarios.atualizarSenhaHash(usuarioId, novoHashDaSenha);
            }
            usuarios.registrarLogin(usuarioId, agora);
            tokens.save(RefreshToken.novo(
                    usuarioId,
                    UUID.randomUUID(),
                    TokenDeRenovacao.hash(refresh).orElseThrow(),
                    agora,
                    VALIDADE,
                    ip,
                    userAgent));
        });
        return refresh;
    }

    Optional<Renovada> renovar(String refresh, String ip, String userAgent) {
        Optional<byte[]> hash = TokenDeRenovacao.hash(refresh);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        return transacao.execute(status -> renovarNaTransacao(hash.get(), ip, userAgent));
    }

    private Optional<Renovada> renovarNaTransacao(byte[] hash, String ip, String userAgent) {
        Optional<RefreshToken> encontrado = tokens.buscarParaRenovar(hash);
        if (encontrado.isEmpty() || encontrado.get().revogado()) {
            return Optional.empty();
        }
        RefreshToken atual = encontrado.get();
        Instant agora = agora();
        if (atual.getUsadoEm() != null) {
            if (Duration.between(atual.getUsadoEm(), agora).compareTo(TOLERANCIA_REUSO) > 0) {
                tokens.revogarFamilia(atual.getFamiliaId(), agora, "reuso");
                LOG.warn("Refresh reutilizado, sessão do aparelho revogada: {}", atual.getUsuarioId());
            }
            return Optional.empty();
        }
        if (atual.vencidoEm(agora)) {
            return Optional.empty();
        }
        Usuario usuario = usuarios.findById(atual.getUsuarioId()).orElseThrow();
        if (Usuario.SUSPENSO.equals(usuario.getStatus()) || Usuario.EXCLUIDO.equals(usuario.getStatus())) {
            tokens.revogarFamilia(atual.getFamiliaId(), agora, "admin");
            LOG.info("Renovação recusada (conta {}): {}", usuario.getStatus(), usuario.getId());
            return Optional.empty();
        }
        String novo = TokenDeRenovacao.gerar();
        RefreshToken sucessor = RefreshToken.novo(
                usuario.getId(),
                atual.getFamiliaId(),
                TokenDeRenovacao.hash(novo).orElseThrow(),
                agora,
                VALIDADE,
                ip,
                userAgent);
        tokens.save(sucessor);
        atual.usar(agora, sucessor.getId());
        return Optional.of(new Renovada(UsuarioResumo.de(usuario), novo));
    }

    /** Sai deste aparelho; cookie de outra pessoa (ou inválido) não revoga nada. */
    void sair(UUID usuarioId, String refresh) {
        TokenDeRenovacao.hash(refresh)
                .ifPresent(hash -> transacao.executeWithoutResult(status -> tokens.findByTokenHash(hash)
                        .filter(token -> token.getUsuarioId().equals(usuarioId))
                        .ifPresent(token -> tokens.revogarFamilia(token.getFamiliaId(), agora(), "logout"))));
    }

    void sairDeTodos(UUID usuarioId) {
        transacao.executeWithoutResult(status -> tokens.revogarDoUsuario(usuarioId, agora(), "sair_todos"));
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS); // precisão do timestamptz
    }
}
