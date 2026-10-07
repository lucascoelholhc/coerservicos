package br.com.coe.servicos.usuario;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.mensageria.Mascara;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;
import br.com.coe.servicos.config.ChaveDeCodigos;

/**
 * Código SMS (CORE-04; RF01, RN08): 6 dígitos do {@link SecureRandom}, guardado como HMAC-SHA256
 * (celular + finalidade + código) e conferido em tempo constante. Vale 5 min, uma vez, com 5
 * tentativas (a 5ª errada invalida); um código novo invalida o anterior. Por celular, no máximo 1
 * envio a cada 60 s e 5 por hora, contados no banco. O SMS sai depois do commit, em fila própria
 * ({@link MensagemPronta}): o código nunca vai em texto para o banco. Conferir devolve o resultado (não lança) para
 * que a tentativa errada fique gravada. Contador de falhas (07/10): {@code falhas-para-bloqueio}
 * erros na janela bloqueiam toda entrada por código da conta até {@code duracao-bloqueio} depois do
 * último erro (valores em {@code coe.seguranca.codigos.*}).
 */
@Service
class ServicoDeCodigoSms {

    static final Duration VALIDADE = Duration.ofMinutes(5);
    static final int MAXIMO_TENTATIVAS = 5;
    static final Duration INTERVALO_ENTRE_ENVIOS = Duration.ofSeconds(60);
    static final int MAXIMO_ENVIOS_POR_HORA = 5;

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final int CODIGOS_POSSIVEIS = 1_000_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeCodigoSms.class);

    private final CodigoSmsRepository codigos;
    private final ChaveDeCodigos chave;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final int falhasParaBloqueio;
    private final Duration janelaDeFalhas;
    private final Duration duracaoDoBloqueio;

    @SuppressWarnings("java:S107") // dependências + os três parâmetros do contador de falhas
    ServicoDeCodigoSms(
            CodigoSmsRepository codigos,
            ChaveDeCodigos chave,
            ApplicationEventPublisher eventos,
            PlatformTransactionManager transacoes,
            Clock clock,
            @Value("${coe.seguranca.codigos.falhas-para-bloqueio:10}") int falhasParaBloqueio,
            @Value("${coe.seguranca.codigos.janela-falhas:24h}") Duration janelaDeFalhas,
            @Value("${coe.seguranca.codigos.duracao-bloqueio:1h}") Duration duracaoDoBloqueio) {
        if (falhasParaBloqueio < 1 || !janelaDeFalhas.isPositive() || !duracaoDoBloqueio.isPositive()) {
            throw new IllegalStateException(
                    "coe.seguranca.codigos inválido: falhas-para-bloqueio >= 1 e janela e" + " bloqueio positivos");
        }
        this.falhasParaBloqueio = falhasParaBloqueio;
        this.janelaDeFalhas = janelaDeFalhas;
        this.duracaoDoBloqueio = duracaoDoBloqueio;
        this.codigos = codigos;
        this.chave = chave;
        this.eventos = eventos;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    /** Envia um código novo; {@code false} se o limite de envios do celular foi atingido. */
    boolean enviar(UUID usuarioId, String celular, FinalidadeSms finalidade, String ip) {
        return emitir(usuarioId, celular, finalidade, null, ip);
    }

    /**
     * Segundo passo do login: envia o código e devolve o desafio (aleatório, só o hash no banco,
     * preso ao usuário). Vazio se o limite de envios foi atingido.
     */
    Optional<String> emitirDesafio(UUID usuarioId, String celular, String ip) {
        String desafio = TokenDeRenovacao.gerar();
        byte[] hash = TokenDeRenovacao.hash(desafio).orElseThrow();
        return emitir(usuarioId, celular, FinalidadeSms.MFA, hash, ip) ? Optional.of(desafio) : Optional.empty();
    }

    /**
     * Entrada por código bloqueada para a conta: {@code falhas-para-bloqueio} erros ou mais na janela
     * (somando as finalidades) e o último há menos de {@code duracao-bloqueio}.
     */
    boolean bloqueado(UUID usuarioId) {
        Instant agora = clock.instant();
        Instant desde = agora.minus(janelaDeFalhas);
        boolean bloqueado = bloqueio(
                agora,
                codigos.somarErrosDaConta(usuarioId, desde).longValue(),
                () -> codigos.ultimoErroDaConta(usuarioId, desde));
        if (bloqueado) {
            LOG.warn("Entrada por código bloqueada (muitos códigos errados): {}", usuarioId);
        }
        return bloqueado;
    }

    /** Prova de posse bloqueada para o número (o código de posse não tem dono: conta pelo celular). */
    boolean bloqueadoParaPosse(String celular) {
        Instant agora = clock.instant();
        Instant desde = agora.minus(janelaDeFalhas);
        boolean bloqueado = bloqueio(
                agora,
                codigos.somarErrosDePosse(celular, desde).longValue(),
                () -> codigos.ultimoErroDePosse(celular, desde));
        if (bloqueado) {
            LOG.warn("Prova de posse bloqueada (muitos códigos errados): {}", Mascara.celular(celular));
        }
        return bloqueado;
    }

    private boolean bloqueio(Instant agora, long erros, java.util.function.Supplier<Optional<Instant>> ultimoErro) {
        return erros >= falhasParaBloqueio
                && ultimoErro
                        .get()
                        .map(ultimo -> ultimo.isAfter(agora.minus(duracaoDoBloqueio)))
                        .orElse(false);
    }

    /**
     * Confere o código ativo da conta para a finalidade e devolve o número a que ele está preso (o
     * do PUT), nunca um valor da requisição. Vazio se errado, vencido ou inexistente.
     */
    Optional<String> conferirDaConta(UUID usuarioId, FinalidadeSms finalidade, String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transacao.execute(status -> codigos.buscarAtivoDaConta(usuarioId, finalidade.valor())
                .filter(ativo -> tentar(ativo, codigo))
                .map(CodigoSms::getCelular)
                .orElse(null)));
    }

    /** Dono do desafio do login, sem conferir nada (para checar o bloqueio antes da tentativa). */
    Optional<UUID> donoDoDesafio(String desafio) {
        return TokenDeRenovacao.hash(desafio).flatMap(codigos::buscarDonoDoDesafio);
    }

    /** Confere o código ativo do celular para a finalidade; certo = usado (uma vez só). */
    boolean conferir(String celular, FinalidadeSms finalidade, String codigo) {
        if (celular == null || codigo == null) {
            return false;
        }
        Boolean certo = transacao.execute(status -> codigos.buscarAtivo(celular, finalidade.valor())
                .map(ativo -> tentar(ativo, codigo))
                .orElse(false));
        return Boolean.TRUE.equals(certo);
    }

    /** Confere o código do desafio do login; certo devolve o dono do desafio. */
    Optional<UUID> conferirDesafio(String desafio, String codigo) {
        Optional<byte[]> hash = TokenDeRenovacao.hash(desafio);
        if (hash.isEmpty() || codigo == null) {
            return Optional.empty();
        }
        UUID dono = transacao.execute(status -> codigos.buscarPorDesafio(hash.get())
                .filter(CodigoSms::ativo)
                .filter(encontrado -> tentar(encontrado, codigo))
                .map(CodigoSms::getUsuarioId)
                .orElse(null));
        return Optional.ofNullable(dono);
    }

    private boolean emitir(UUID usuarioId, String celular, FinalidadeSms finalidade, byte[] desafioHash, String ip) {
        String codigo = "%06d".formatted(ALEATORIO.nextInt(CODIGOS_POSSIVEIS));
        Boolean gravou = transacao.execute(status -> {
            Instant agora = clock.instant();
            codigos.travar("codigo_sms:" + celular);
            if (limiteAtingido(celular, agora)) {
                return false;
            }
            codigos.buscarAtivo(celular, finalidade.valor()).ifPresent(anterior -> anterior.invalidar(agora));
            codigos.flush(); // o anterior sai do índice de ativos antes do INSERT do novo
            codigos.save(CodigoSms.novo(
                    usuarioId,
                    celular,
                    finalidade.valor(),
                    hmac(celular, finalidade.valor(), codigo),
                    desafioHash,
                    agora,
                    agora.plus(VALIDADE),
                    ip));
            eventos.publishEvent(MensagemPronta.sms(
                    celular, "COE: seu código é " + codigo + ". Vale por 5 minutos. Não passe para ninguém."));
            return true;
        });
        if (!Boolean.TRUE.equals(gravou)) {
            LOG.info("Envio de código SMS barrado pelo limite: {}", Mascara.celular(celular));
            return false;
        }
        LOG.info("Código SMS gerado ({}) para {}", finalidade.valor(), Mascara.celular(celular));
        return true;
    }

    private boolean limiteAtingido(String celular, Instant agora) {
        return codigos.contarEnviosDesde(celular, agora.minus(INTERVALO_ENTRE_ENVIOS)) > 0
                || codigos.contarEnviosDesde(celular, agora.minus(UMA_HORA)) >= MAXIMO_ENVIOS_POR_HORA;
    }

    private boolean tentar(CodigoSms codigoSms, String codigo) {
        Instant agora = clock.instant();
        if (codigoSms.vencidoEm(agora)) {
            return false;
        }
        byte[] recebido = hmac(codigoSms.getCelular(), codigoSms.getFinalidade(), codigo);
        if (MessageDigest.isEqual(codigoSms.getCodigoHmac(), recebido)) {
            codigoSms.usar(agora);
            return true;
        }
        codigoSms.registrarErro(agora, MAXIMO_TENTATIVAS);
        return false;
    }

    private byte[] hmac(String celular, String finalidade, String codigo) {
        return chave.hmac(celular + ":" + finalidade + ":" + codigo);
    }
}
