package br.com.coe.servicos.usuario;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Cadastro de cliente (CORE-02; RF03, RN58, RNF18). Numa transação: usuário ativo, papel CLIENTE e
 * aceite dos termos. Não faz login (CORE-03) nem confirma o celular por SMS (CORE-04).
 *
 * <p>O hash da senha (BCrypt custo 12, centenas de ms) é calculado <b>antes</b> de abrir a transação,
 * para não prender uma conexão do pool num endpoint público.
 */
@Service
public class CadastroClienteService {

    private static final Logger LOG = LoggerFactory.getLogger(CadastroClienteService.class);

    private final UsuarioRepository usuarios;
    private final AceiteTermosRepository aceites;
    private final PasswordEncoder codificadorDeSenha;
    private final ConfiguracaoNegocio configuracao;
    private final TransactionTemplate transacao;
    private final ReivindicacaoDeContato reivindicacao;
    private final ServicoDeConfirmacaoDeEmail confirmacaoDeEmail;

    CadastroClienteService(
            UsuarioRepository usuarios,
            AceiteTermosRepository aceites,
            PasswordEncoder codificadorDeSenha,
            ConfiguracaoNegocio configuracao,
            PlatformTransactionManager transacoes,
            ReivindicacaoDeContato reivindicacao,
            ServicoDeConfirmacaoDeEmail confirmacaoDeEmail) {
        this.reivindicacao = reivindicacao;
        this.confirmacaoDeEmail = confirmacaoDeEmail;
        this.usuarios = usuarios;
        this.aceites = aceites;
        this.codificadorDeSenha = codificadorDeSenha;
        this.configuracao = configuracao;
        this.transacao = new TransactionTemplate(transacoes);
    }

    public ClienteCriadoResponse cadastrar(NovoClienteRequest pedido, String ip, String userAgent) {
        String versaoVigente = configuracao.versaoTermos();
        if (!versaoVigente.equals(pedido.versaoTermosAceita())) {
            throw new RegraDeNegocioException(
                    "termos-desatualizados", "Os termos de uso mudaram. Leia e aceite a versão atual.");
        }
        String celular = Contato.normalizarCelular(pedido.celular());
        String email = Contato.normalizarEmail(pedido.email());
        // Checagem prévia barata, antes do hash; a corrida que passar daqui é barrada pelo UNIQUE e
        // pela trava da reivindicação (RN61), que confere tudo de novo dentro da transação.
        Optional<Usuario> donoDoCelular = usuarios.findByCelular(celular);
        if (donoDoCelular.isPresent()) {
            if (donoDoCelular.get().isCelularConfirmado()) {
                throw celularJaCadastrado();
            }
            if (pedido.comprovanteCelular() == null) {
                throw podeSerReivindicado(
                        "celular", "Este celular está em outra conta, sem confirmação. Confirme que ele é seu.");
            }
        }
        Optional<Usuario> donoDoEmail = usuarios.findByEmail(email);
        if (donoDoEmail.isPresent()) {
            if (donoDoEmail.get().isEmailConfirmado()) {
                throw emailJaCadastrado();
            }
            if (pedido.comprovanteEmail() == null) {
                throw podeSerReivindicado(
                        "email", "Este e-mail está em outra conta, sem confirmação. Confirme que ele é seu.");
            }
        }
        Usuario usuario = Usuario.novoCliente(
                Contato.normalizarNome(pedido.nome()),
                celular,
                email,
                codificadorDeSenha.encode(pedido.senha()),
                Contato.normalizarCep(pedido.cep()));
        AceiteTermos aceite = new AceiteTermos(usuario.getId(), versaoVigente, ip, userAgent);
        try {
            transacao.executeWithoutResult(status -> {
                reivindicacao.travarContatos(celular, email);
                if (pedido.comprovanteCelular() != null || pedido.comprovanteEmail() != null) {
                    ReivindicacaoDeContato.Resultado comprovado = reivindicacao.aplicar(
                            usuario.getId(),
                            celular,
                            pedido.comprovanteCelular(),
                            email,
                            pedido.comprovanteEmail(),
                            ip);
                    if (comprovado.celularComprovado()) {
                        usuario.confirmarCelular(comprovado.em());
                    }
                    if (comprovado.emailComprovado()) {
                        usuario.confirmarEmail(comprovado.em());
                    }
                }
                usuarios.saveAndFlush(usuario);
                if (!usuario.isEmailConfirmado()) {
                    confirmacaoDeEmail.enviarLinkDoCadastro(usuario.getId(), email);
                }
                aceites.save(aceite);
            });
        } catch (DataIntegrityViolationException erro) {
            throw traduzirDuplicidade(erro);
        }
        LOG.info("Cliente cadastrado: {}", usuario.getId());
        return new ClienteCriadoResponse(usuario.getId(), usuario.getNome());
    }

    /** Dois cadastros ao mesmo tempo: o segundo bate no UNIQUE do banco e vira o mesmo 409. */
    private static RuntimeException traduzirDuplicidade(DataIntegrityViolationException erro) {
        String constraint = nomeDaConstraint(erro);
        if ("uq_usuario_celular".equals(constraint)) {
            return celularJaCadastrado();
        }
        if ("uq_usuario_email".equals(constraint)) {
            return emailJaCadastrado();
        }
        return erro;
    }

    private static String nomeDaConstraint(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao) {
                return violacao.getConstraintName();
            }
        }
        return null;
    }

    private static ConflitoException celularJaCadastrado() {
        return ContatoEmUso.no("celular");
    }

    /** RN61: o dado é de outra conta que nunca o confirmou; quem provar a posse fica com ele. */
    private static ConflitoException podeSerReivindicado(String campo, String mensagem) {
        return ContatoEmUso.no(campo);
    }

    private static ConflitoException emailJaCadastrado() {
        return ContatoEmUso.no("email");
    }
}
