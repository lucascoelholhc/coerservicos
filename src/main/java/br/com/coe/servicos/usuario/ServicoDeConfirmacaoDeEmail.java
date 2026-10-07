package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Confirmar o e-mail por link (CORE-04; RN61, RF03): 24 h, uso único, token no fragmento. O link só
 * vale para o e-mail que a conta ainda tem; reusado, vencido, inventado ou de outro e-mail dá o
 * mesmo 422 link-invalido. Conta sem e-mail (RN61, perdeu para o dono): o PUT manda o link para o
 * endereço novo e a confirmação grava esse endereço (o do link, nunca da requisição), se ainda livre.
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

    /**
     * Cadastro novo (07/10): manda o link de confirmação sozinho, na transação do cadastro (sai só
     * depois do commit; rollback não manda). Limite atingido só vai para o log.
     */
    void enviarLinkDoCadastro(java.util.UUID usuarioId, String email) {
        if (!tokens.enviarLink(usuarioId, email, FinalidadeToken.CONFIRMAR_EMAIL, VALIDADE, EMAIL)) {
            LOG.info("Link de confirmação do cadastro não enviado (limite): {}", usuarioId);
        }
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

    /** RN61: só com o campo vazio e e-mail livre; manda o link, sem gravar nada na conta. */
    void cadastrarEmail(String digitado) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (usuario.getEmail() != null) {
            throw new ConflitoException(
                    "email-ja-preenchido", "Sua conta já tem e-mail. Para trocar, fale com a equipe da COE.", "email");
        }
        String email = Contato.normalizarEmail(digitado);
        if (usuarios.existsByEmail(email)) {
            throw emailJaCadastrado();
        }
        if (!tokens.enviarLink(usuario.getId(), email, FinalidadeToken.CONFIRMAR_EMAIL, VALIDADE, EMAIL)) {
            throw new MuitasTentativasException("Você pediu muitos e-mails. Aguarde um pouco e tente de novo.");
        }
    }

    private enum Confirmacao {
        FEITA,
        LINK_INVALIDO,
        EMAIL_TOMADO
    }

    void confirmar(String token) {
        Confirmacao resultado;
        try {
            resultado = transacao.execute(status -> tokens.consumir(token, FinalidadeToken.CONFIRMAR_EMAIL)
                    .map(this::aplicar)
                    .orElse(Confirmacao.LINK_INVALIDO));
        } catch (DataIntegrityViolationException corrida) {
            resultado = Confirmacao.EMAIL_TOMADO;
        }
        if (resultado == Confirmacao.EMAIL_TOMADO) {
            throw emailJaCadastrado();
        }
        if (resultado != Confirmacao.FEITA) {
            throw new RegraDeNegocioException("link-invalido", "Este link não vale mais. Peça um novo.");
        }
    }

    private Confirmacao aplicar(TokenVerificacao link) {
        Usuario usuario = usuarios.findById(link.getUsuarioId())
                .filter(conta -> !Usuario.EXCLUIDO.equals(conta.getStatus()))
                .orElse(null);
        if (usuario == null) {
            return Confirmacao.LINK_INVALIDO;
        }
        if (usuario.getEmail() == null) {
            if (usuarios.existsByEmail(link.getDestino())) {
                return Confirmacao.EMAIL_TOMADO;
            }
            usuario.gravarEmailConfirmado(link.getDestino(), clock.instant());
            usuarios.saveAndFlush(usuario);
            LOG.info("E-mail novo gravado já confirmado: {}", usuario.getId());
            return Confirmacao.FEITA;
        }
        if (!link.getDestino().equals(usuario.getEmail())) {
            return Confirmacao.LINK_INVALIDO;
        }
        usuario.confirmarEmail(clock.instant());
        LOG.info("E-mail confirmado: {}", usuario.getId());
        return Confirmacao.FEITA;
    }

    private static ConflitoException emailJaCadastrado() {
        return new ConflitoException("email-ja-cadastrado", "Este e-mail já está em outra conta.", "email");
    }
}
