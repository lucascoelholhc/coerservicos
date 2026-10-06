package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessões por aparelho (PA03): refresh de 30 dias, renovado a cada uso; reuso depois de 30 s
 * revoga a família (uma família = um login = um aparelho). As recusas DEVOLVEM um resultado em vez
 * de lançar exceção dentro da transação, para a revogação ser gravada (exceção faria rollback).
 */
@Service
class ServicoDeSessao {

    static final Duration VALIDADE = Duration.ofDays(30);
    static final Duration TOLERANCIA_REUSO = Duration.ofSeconds(30);
    private static final Duration TETO_MINIMO = Duration.ofDays(1);
    private static final Duration TETO_MAXIMO = Duration.ofDays(365);

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeSessao.class);

    sealed interface Resultado permits Renovada, Recusada {}

    record Renovada(UsuarioResumo usuario, RefreshEmitido refresh) implements Resultado {}

    /**
     * Renovação recusada. {@code apagarCookie} é falso só na corrida benigna (outra aba renovou há
     * menos de 30 s): apagar o cookie ali derrubaria o cookie novo que a outra aba acabou de receber.
     */
    record Recusada(boolean apagarCookie) implements Resultado {}

    private static final Recusada RECUSADA = new Recusada(true);
    private static final Recusada CORRIDA_BENIGNA = new Recusada(false);

    private final RefreshTokenRepository tokens;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final Duration duracaoMaxima;

    ServicoDeSessao(
            RefreshTokenRepository tokens,
            UsuarioRepository usuarios,
            PlatformTransactionManager transacoes,
            Clock clock,
            @Value("${coe.auth.sessao.duracao-maxima}") Duration duracaoMaxima) {
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
        if (duracaoMaxima.compareTo(TETO_MINIMO) < 0 || duracaoMaxima.compareTo(TETO_MAXIMO) > 0) {
            throw new IllegalStateException(
                    "coe.auth.sessao.duracao-maxima precisa ficar entre 1 e 365 dias: " + duracaoMaxima);
        }
        this.duracaoMaxima = duracaoMaxima;
    }

    /** Vale 30 dias, mas nunca além do teto da sessão (início + 90 dias). */
    private Instant expiracao(Instant agora, Instant inicioDaSessao) {
        Instant pelaValidade = agora.plus(VALIDADE);
        Instant peloTeto = inicioDaSessao.plus(duracaoMaxima);
        return pelaValidade.isBefore(peloTeto) ? pelaValidade : peloTeto;
    }

    /**
     * Login aceito: regrava o hash da senha (se veio um novo, e só se ninguém trocou a senha no meio
     * do caminho), marca o login e abre a sessão do aparelho.
     */
    RefreshEmitido abrir(UUID usuarioId, String hashAntigo, String hashNovo, String ip, String userAgent) {
        String refresh = TokenDeRenovacao.gerar();
        Instant agora = agora();
        Instant expiraEm = expiracao(agora, agora);
        transacao.executeWithoutResult(status -> {
            if (hashNovo != null) {
                usuarios.atualizarSenhaHash(usuarioId, hashAntigo, hashNovo);
            }
            usuarios.registrarLogin(usuarioId, agora);
            tokens.save(RefreshToken.novo(
                    usuarioId,
                    UUID.randomUUID(),
                    TokenDeRenovacao.hash(refresh).orElseThrow(),
                    agora,
                    expiraEm,
                    agora,
                    ip,
                    userAgent));
        });
        return new RefreshEmitido(refresh, Duration.between(agora, expiraEm));
    }

    Resultado renovar(String refresh, String ip, String userAgent) {
        Optional<byte[]> hash = TokenDeRenovacao.hash(refresh);
        if (hash.isEmpty()) {
            return RECUSADA;
        }
        return transacao.execute(status -> renovarNaTransacao(hash.get(), ip, userAgent));
    }

    private Resultado renovarNaTransacao(byte[] hash, String ip, String userAgent) {
        Optional<RefreshToken> encontrado = tokens.buscarParaRenovar(hash);
        if (encontrado.isEmpty() || encontrado.get().revogado()) {
            return RECUSADA;
        }
        RefreshToken atual = encontrado.get();
        Instant agora = agora();
        if (atual.getUsadoEm() != null) {
            if (Duration.between(atual.getUsadoEm(), agora).compareTo(TOLERANCIA_REUSO) <= 0) {
                return CORRIDA_BENIGNA;
            }
            tokens.revogarFamilia(atual.getFamiliaId(), agora, "reuso");
            LOG.warn("Refresh reutilizado, sessão do aparelho revogada: {}", atual.getUsuarioId());
            return RECUSADA;
        }
        if (!agora.isBefore(atual.getSessaoIniciadaEm().plus(duracaoMaxima))) {
            tokens.revogarFamilia(atual.getFamiliaId(), agora, "teto");
            LOG.info("Sessão chegou ao teto de {}: {}", duracaoMaxima, atual.getUsuarioId());
            return RECUSADA;
        }
        if (atual.vencidoEm(agora)) {
            return RECUSADA;
        }
        Optional<Usuario> dono = usuarios.findById(atual.getUsuarioId());
        if (dono.isEmpty() || !podeRenovar(dono.get())) {
            tokens.revogarFamilia(atual.getFamiliaId(), agora, "admin");
            LOG.info("Renovação recusada (conta sem acesso): {}", atual.getUsuarioId());
            return RECUSADA;
        }
        Usuario usuario = dono.get();
        String novo = TokenDeRenovacao.gerar();
        Instant expiraEm = expiracao(agora, atual.getSessaoIniciadaEm());
        RefreshToken sucessor = RefreshToken.novo(
                usuario.getId(),
                atual.getFamiliaId(),
                TokenDeRenovacao.hash(novo).orElseThrow(),
                agora,
                expiraEm,
                atual.getSessaoIniciadaEm(),
                ip,
                userAgent);
        tokens.save(sucessor);
        atual.usar(agora, sucessor.getId());
        return new Renovada(UsuarioResumo.de(usuario), new RefreshEmitido(novo, Duration.between(agora, expiraEm)));
    }

    private static boolean podeRenovar(Usuario usuario) {
        return !Usuario.SUSPENSO.equals(usuario.getStatus()) && !Usuario.EXCLUIDO.equals(usuario.getStatus());
    }

    /**
     * Sai deste aparelho. Trava a linha como a renovação: se as duas chegarem juntas, a saída espera
     * a renovação terminar e revoga também o sucessor. Cookie de outra pessoa não revoga nada.
     */
    void sair(UUID usuarioId, String refresh) {
        TokenDeRenovacao.hash(refresh)
                .ifPresent(hash -> transacao.executeWithoutResult(status -> tokens.buscarParaRenovar(hash)
                        .filter(token -> token.getUsuarioId().equals(usuarioId))
                        .ifPresent(token -> tokens.revogarFamilia(token.getFamiliaId(), agora(), "logout"))));
    }

    /**
     * Revoga todas as sessões do usuário. Uma renovação que já tinha travado o token antes pode
     * concluir em seguida; o sucessor dela cai no próximo "sair de todos" ou vence em 30 dias (janela
     * aceita, anotada no plano).
     */
    void sairDeTodos(UUID usuarioId) {
        transacao.executeWithoutResult(status -> tokens.revogarDoUsuario(usuarioId, agora(), "sair_todos"));
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS); // precisão do timestamptz
    }
}
