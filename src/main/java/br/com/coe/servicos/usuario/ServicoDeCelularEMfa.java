package br.com.coe.servicos.usuario;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Confirmar o celular (RN08), recolocar o celular perdido (RN61) e ligar/desligar o MFA (CORE-04),
 * sempre na conta de quem chama. O código é conferido na transação dele (a tentativa errada fica
 * gravada); a conta muda em outra. Celular novo (campo vazio): o PUT só manda o código, preso ao
 * número; a confirmação grava esse número já confirmado, se ele ainda estiver livre.
 */
@Service
class ServicoDeCelularEMfa {

    static final String MENSAGEM_CODIGO_ENVIADO = "Enviamos um código por SMS para o seu celular.";

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeCelularEMfa.class);

    private final ServicoDeCodigoSms codigos;
    private final ContaDeQuemChama contaDeQuemChama;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ServicoDeCelularEMfa(
            ServicoDeCodigoSms codigos,
            ContaDeQuemChama contaDeQuemChama,
            UsuarioRepository usuarios,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.codigos = codigos;
        this.contaDeQuemChama = contaDeQuemChama;
        this.usuarios = usuarios;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    void pedirCodigoDeConfirmacao(String ip) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (usuario.getCelular() == null) {
            throw new RegraDeNegocioException("celular-nao-cadastrado", "Cadastre um celular antes de confirmar.");
        }
        if (usuario.isCelularConfirmado()) {
            throw new ConflitoException("celular-ja-confirmado", "Seu celular já está confirmado.", null);
        }
        enviar(usuario, usuario.getCelular(), FinalidadeSms.VERIFICAR_CELULAR, ip);
    }

    /** RN61: só com o campo vazio e número livre; manda o código, sem gravar nada na conta. */
    void cadastrarCelular(String digitado, String ip) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (usuario.getCelular() != null) {
            throw new ConflitoException(
                    "celular-ja-preenchido",
                    "Sua conta já tem celular. Para trocar, fale com a equipe da COE.",
                    "celular");
        }
        String celular = Contato.normalizarCelular(digitado);
        if (usuarios.existsByCelular(celular)) {
            throw celularJaCadastrado();
        }
        enviar(usuario, celular, FinalidadeSms.TROCAR_CELULAR, ip);
    }

    void confirmarCelular(String codigo) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (usuario.getCelular() == null) {
            gravarCelularNovo(usuario, codigo);
            return;
        }
        if (usuario.isCelularConfirmado()) {
            return;
        }
        exigirCodigo(usuario, FinalidadeSms.VERIFICAR_CELULAR, codigo);
        transacao.executeWithoutResult(
                status -> contaDeQuemChama.carregar(usuario.getId()).confirmarCelular(clock.instant()));
        LOG.info("Celular confirmado: {}", usuario.getId());
    }

    /** O número é o do código (o do PUT), nunca da requisição; o UNIQUE decide a corrida. */
    private void gravarCelularNovo(Usuario usuario, String codigo) {
        String celular = codigos.conferirDaConta(usuario.getId(), FinalidadeSms.TROCAR_CELULAR, codigo)
                .orElseThrow(ServicoDeCelularEMfa::codigoInvalido);
        try {
            transacao.executeWithoutResult(status -> {
                if (usuarios.existsByCelular(celular)) {
                    throw celularJaCadastrado();
                }
                Usuario conta = contaDeQuemChama.carregar(usuario.getId());
                if (conta.getCelular() != null) {
                    throw new ConflitoException("celular-ja-preenchido", "Sua conta já tem celular.", "celular");
                }
                conta.gravarCelularConfirmado(celular, clock.instant());
                usuarios.saveAndFlush(conta);
            });
        } catch (DataIntegrityViolationException corrida) {
            throw celularJaCadastrado();
        }
        LOG.info("Celular novo gravado já confirmado: {}", usuario.getId());
    }

    void pedirCodigoDeMfa(String ip) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        exigirCelularConfirmado(usuario);
        enviar(usuario, usuario.getCelular(), FinalidadeSms.CONFIGURAR_MFA, ip);
    }

    void definirMfa(boolean ativo, String codigo) {
        Usuario usuario = contaDeQuemChama.carregarAtiva();
        if (!ativo && usuario.getPapeis().contains(Papel.ADMIN)) {
            throw new AcaoProibidaException(
                    "mfa-obrigatorio", "Para a equipe da COE, o código por SMS no login é obrigatório.");
        }
        exigirCelularConfirmado(usuario);
        exigirCodigo(usuario, FinalidadeSms.CONFIGURAR_MFA, codigo);
        transacao.executeWithoutResult(
                status -> contaDeQuemChama.carregar(usuario.getId()).definirMfa(ativo));
        LOG.info("MFA {}: {}", ativo ? "ligado" : "desligado", usuario.getId());
    }

    private void enviar(Usuario usuario, String celular, FinalidadeSms finalidade, String ip) {
        if (!codigos.enviar(usuario.getId(), celular, finalidade, ip)) {
            throw new MuitasTentativasException("Você pediu muitos códigos. Aguarde um pouco e tente de novo.");
        }
    }

    private void exigirCodigo(Usuario usuario, FinalidadeSms finalidade, String codigo) {
        if (!codigos.conferir(usuario.getCelular(), finalidade, codigo)) {
            throw codigoInvalido();
        }
    }

    private static RegraDeNegocioException codigoInvalido() {
        return new RegraDeNegocioException("codigo-invalido", "Código incorreto ou vencido. Peça um novo.");
    }

    private static ConflitoException celularJaCadastrado() {
        return ContatoEmUso.no("celular");
    }

    private static void exigirCelularConfirmado(Usuario usuario) {
        if (!usuario.isCelularConfirmado()) {
            throw new AcaoProibidaException(
                    "celular-nao-confirmado", "Confirme o seu celular antes de ligar o código por SMS.");
        }
    }
}
