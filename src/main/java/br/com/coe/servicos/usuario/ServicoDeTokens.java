package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.mensageria.Mascara;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;
import br.com.coe.servicos.config.UrlDoFront;

/**
 * Tokens de 32 bytes por link (CORE-04/05): só o SHA-256 em token_verificacao, uso único, um ativo
 * por destino e finalidade (o novo invalida o anterior). Por endereço, no máximo 1 e-mail a cada
 * 60 s e 5 por hora, contados no banco. O e-mail sai depois do commit ({@link MensagemPronta}).
 */
@Service
class ServicoDeTokens {

    static final Duration INTERVALO_ENTRE_ENVIOS = Duration.ofSeconds(60);
    static final int MAXIMO_ENVIOS_POR_HORA = 5;
    /** Comprovante de posse (RN61): vale para um cadastro, em até 30 min. */
    static final Duration VALIDADE_DO_COMPROVANTE = Duration.ofMinutes(30);

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeTokens.class);

    /** Link pronto para mandar: assunto e texto (o texto recebe o link já montado). */
    record Email(String caminhoNoFront, String assunto, UnaryOperator<String> textoComLink) {}

    private final TokenVerificacaoRepository tokens;
    private final ApplicationEventPublisher eventos;
    private final UrlDoFront front;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ServicoDeTokens(
            TokenVerificacaoRepository tokens,
            ApplicationEventPublisher eventos,
            UrlDoFront front,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.tokens = tokens;
        this.eventos = eventos;
        this.front = front;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    /** Gera o token, grava o hash e manda o link por e-mail; {@code false} se o limite foi atingido. */
    boolean enviarLink(UUID usuarioId, String endereco, FinalidadeToken finalidade, Duration validade, Email email) {
        String token = TokenDeRenovacao.gerar();
        byte[] hash = TokenDeRenovacao.hash(token).orElseThrow();
        Boolean gravou = transacao.execute(status -> {
            Instant agora = clock.instant();
            tokens.travar("token_verificacao:" + endereco);
            if (limiteAtingido(endereco, agora)) {
                return false;
            }
            gravar(usuarioId, TokenVerificacao.CANAL_EMAIL, endereco, finalidade, hash, agora, validade);
            String link = front.link(email.caminhoNoFront(), token);
            eventos.publishEvent(MensagemPronta.email(
                    endereco, email.assunto(), email.textoComLink().apply(link)));
            return true;
        });
        if (!Boolean.TRUE.equals(gravou)) {
            LOG.info("Envio de link barrado pelo limite: {}", Mascara.email(endereco));
            return false;
        }
        LOG.info("Link gerado ({}) para {}", finalidade.valor(), Mascara.email(endereco));
        return true;
    }

    /**
     * Comprovante de posse (RN61): canal ({@code celular} ou {@code email}) e destino já provados;
     * só o hash no banco, sem dono, sem envio. Devolve o token para o cadastro.
     */
    String emitirComprovante(String canal, String destino) {
        String token = TokenDeRenovacao.gerar();
        byte[] hash = TokenDeRenovacao.hash(token).orElseThrow();
        transacao.executeWithoutResult(status -> {
            tokens.travar("token_verificacao:" + destino); // dois comprovantes ao mesmo tempo não dão 500
            gravar(
                    null,
                    canal,
                    destino,
                    FinalidadeToken.COMPROVANTE_POSSE,
                    hash,
                    clock.instant(),
                    VALIDADE_DO_COMPROVANTE);
        });
        return token;
    }

    /** Só olha (sem usar nem travar): o token é da finalidade e vale agora? */
    Optional<TokenVerificacao> espiar(String token, FinalidadeToken finalidade) {
        Instant agora = clock.instant();
        return TokenDeRenovacao.hash(token)
                .flatMap(tokens::buscarSemTravar)
                .filter(encontrado -> finalidade.valor().equals(encontrado.getFinalidade()))
                .filter(encontrado -> encontrado.valeEm(agora));
    }

    /** Usa o token (uma vez) se for da finalidade, estiver ativo e no prazo. */
    Optional<TokenVerificacao> consumir(String token, FinalidadeToken finalidade) {
        return consumir(token, finalidade, encontrado -> true);
    }

    /** Como {@link #consumir(String, FinalidadeToken)}, só usando o token se a condição valer. */
    Optional<TokenVerificacao> consumir(
            String token, FinalidadeToken finalidade, Predicate<TokenVerificacao> condicao) {
        Optional<byte[]> hash = TokenDeRenovacao.hash(token);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(transacao.execute(status -> {
            Instant agora = clock.instant();
            return tokens.buscarPorHash(hash.get())
                    .filter(encontrado -> finalidade.valor().equals(encontrado.getFinalidade()))
                    .filter(encontrado -> encontrado.valeEm(agora))
                    .filter(condicao)
                    .map(encontrado -> {
                        encontrado.usar(agora);
                        return encontrado;
                    })
                    .orElse(null);
        }));
    }

    private void gravar(
            UUID usuarioId,
            String canal,
            String destino,
            FinalidadeToken finalidade,
            byte[] hash,
            Instant agora,
            Duration validade) {
        tokens.buscarAtivo(destino, finalidade.valor()).ifPresent(anterior -> anterior.invalidar(agora));
        tokens.flush(); // o anterior sai do índice de ativos antes do INSERT do novo
        tokens.save(TokenVerificacao.novo(
                usuarioId, canal, destino, finalidade.valor(), hash, agora, agora.plus(validade)));
    }

    private boolean limiteAtingido(String endereco, Instant agora) {
        return tokens.contarEmailsDesde(endereco, agora.minus(INTERVALO_ENTRE_ENVIOS)) > 0
                || tokens.contarEmailsDesde(endereco, agora.minus(UMA_HORA)) >= MAXIMO_ENVIOS_POR_HORA;
    }
}
