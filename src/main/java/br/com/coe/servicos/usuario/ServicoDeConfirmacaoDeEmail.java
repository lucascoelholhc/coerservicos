package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Confirmar o e-mail por link (CORE-04; RN61, RF03): 24 h, uso único, token no fragmento. O link só
 * vale para o e-mail que a conta ainda tem; reusado, vencido, inventado ou de outro e-mail dá o
 * mesmo 422 link-invalido.
 */
@Service
class ServicoDeConfirmacaoDeEmail {

    static final Duration VALIDADE = Duration.ofHours(24);
    static final String MENSAGEM_LINK_ENVIADO = "Enviamos um link para o seu e-mail.";

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeConfirmacaoDeEmail.class);
    private static final ServicoDeTokens.Email EMAIL =
            new ServicoDeTokens.Email("/confirmar-email", "Confirme seu e-mail na COE", link -> """
                    Olá!

                    Para confirmar seu e-mail na COE, abra o link abaixo (ele vale por 24 horas):

                    %s

                    Se não foi você quem pediu, é só ignorar este e-mail.

                    Equipe COE
                    """.formatted(link));

    private final ContaDeQuemChama contaDeQuemChama;
    private final UsuarioRepository usuarios;
    private final ServicoDeTokens tokens;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ServicoDeConfirmacaoDeEmail(
            ContaDeQuemChama contaDeQuemChama,
            UsuarioRepository usuarios,
            ServicoDeTokens tokens,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.contaDeQuemChama = contaDeQuemChama;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    void pedirLink() {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (usuario.getEmail() == null) {
            throw new RegraDeNegocioException("email-nao-cadastrado", "Cadastre um e-mail antes de confirmar.");
        }
        if (usuario.isEmailConfirmado()) {
            throw new ConflitoException("email-ja-confirmado", "Seu e-mail já está confirmado.", null);
        }
        if (!tokens.enviarLink(usuario.getId(), usuario.getEmail(), FinalidadeToken.CONFIRMAR_EMAIL, VALIDADE, EMAIL)) {
            throw new MuitasTentativasException("Você pediu muitos e-mails. Aguarde um pouco e tente de novo.");
        }
    }

    void confirmar(String token) {
        Boolean confirmou = transacao.execute(status -> tokens.consumir(token, FinalidadeToken.CONFIRMAR_EMAIL)
                .flatMap(link -> usuarios.findById(link.getUsuarioId())
                        .filter(usuario -> !Usuario.EXCLUIDO.equals(usuario.getStatus()))
                        .filter(usuario -> link.getDestino().equals(usuario.getEmail())))
                .map(usuario -> {
                    usuario.confirmarEmail(clock.instant());
                    LOG.info("E-mail confirmado: {}", usuario.getId());
                    return true;
                })
                .orElse(false));
        if (!Boolean.TRUE.equals(confirmou)) {
            throw new RegraDeNegocioException("link-invalido", "Este link não vale mais. Peça um novo.");
        }
    }
}
