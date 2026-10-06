package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Confirmar o celular (RN08) e ligar/desligar o MFA (CORE-04), sempre na conta de quem chama (o id
 * vem só do token). O código é conferido na transação dele (a tentativa errada fica gravada); a
 * conta muda em outra.
 */
@Service
class ServicoDeCelularEMfa {

    static final String MENSAGEM_CODIGO_ENVIADO = "Enviamos um código por SMS para o seu celular.";

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeCelularEMfa.class);

    private final UsuarioRepository usuarios;
    private final ServicoDeCodigoSms codigos;
    private final UsuarioAutenticado usuarioAutenticado;
    private final TransactionTemplate transacao;
    private final Clock clock;

    ServicoDeCelularEMfa(
            UsuarioRepository usuarios,
            ServicoDeCodigoSms codigos,
            UsuarioAutenticado usuarioAutenticado,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.usuarioAutenticado = usuarioAutenticado;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    void pedirCodigoDeConfirmacao(String ip) {
        Usuario usuario = carregar(usuarioAutenticado.id());
        if (usuario.isCelularConfirmado()) {
            throw new ConflitoException("celular-ja-confirmado", "Seu celular já está confirmado.", null);
        }
        enviar(usuario, FinalidadeSms.VERIFICAR_CELULAR, ip);
    }

    void confirmarCelular(String codigo) {
        Usuario usuario = carregar(usuarioAutenticado.id());
        if (usuario.isCelularConfirmado()) {
            return;
        }
        exigirCodigo(usuario, FinalidadeSms.VERIFICAR_CELULAR, codigo);
        transacao.executeWithoutResult(status -> carregar(usuario.getId()).confirmarCelular(clock.instant()));
        LOG.info("Celular confirmado: {}", usuario.getId());
    }

    void pedirCodigoDeMfa(String ip) {
        Usuario usuario = carregar(usuarioAutenticado.id());
        exigirCelularConfirmado(usuario);
        enviar(usuario, FinalidadeSms.CONFIGURAR_MFA, ip);
    }

    void definirMfa(boolean ativo, String codigo) {
        Usuario usuario = carregar(usuarioAutenticado.id());
        if (!ativo && usuario.getPapeis().contains(Papel.ADMIN)) {
            throw new AcaoProibidaException(
                    "mfa-obrigatorio", "Para a equipe da COE, o código por SMS no login é obrigatório.");
        }
        exigirCelularConfirmado(usuario);
        exigirCodigo(usuario, FinalidadeSms.CONFIGURAR_MFA, codigo);
        transacao.executeWithoutResult(status -> carregar(usuario.getId()).definirMfa(ativo));
        LOG.info("MFA {}: {}", ativo ? "ligado" : "desligado", usuario.getId());
    }

    private Usuario carregar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }

    private void enviar(Usuario usuario, FinalidadeSms finalidade, String ip) {
        if (!codigos.enviar(usuario.getId(), usuario.getCelular(), finalidade, ip)) {
            throw new MuitasTentativasException("Você pediu muitos códigos. Aguarde um pouco e tente de novo.");
        }
    }

    private void exigirCodigo(Usuario usuario, FinalidadeSms finalidade, String codigo) {
        if (!codigos.conferir(usuario.getCelular(), finalidade, codigo)) {
            throw new RegraDeNegocioException("codigo-invalido", "Código incorreto ou vencido. Peça um novo.");
        }
    }

    private static void exigirCelularConfirmado(Usuario usuario) {
        if (!usuario.isCelularConfirmado()) {
            throw new AcaoProibidaException(
                    "celular-nao-confirmado", "Confirme o seu celular antes de ligar o código por SMS.");
        }
    }
}
