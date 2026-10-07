package br.com.coe.servicos.usuario;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.usuario.validacao.SenhaPermitidaValidador;

/**
 * Senha (CORE-05; RF01). Esqueci: o mesmo 202 sempre; link (30 min) só para e-mail CONFIRMADO,
 * senão código SMS no celular confirmado, senão nada (suporte): link para e-mail não confirmado
 * abriria a tomada de conta que a RN61 fecha. Redefinir: mesma regra do cadastro, BCrypt fora da
 * transação, invalida os outros links e códigos de senha e revoga todas as sessões (troca_senha),
 * sem entrar. Trocar (logado): revoga as outras sessões e mantém a deste aparelho.
 */
@Service
class ServicoDeSenha {

    static final String MENSAGEM_ESQUECI = "Se houver uma conta com esse login, enviamos as instruções.";
    static final Duration VALIDADE_DO_LINK = Duration.ofMinutes(30);

    private static final String MOTIVO = "troca_senha";
    private static final int MAXIMO_BYTES_SENHA = 72;
    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeSenha.class);
    private static final ServicoDeTokens.Email EMAIL =
            new ServicoDeTokens.Email("/redefinir-senha", "Redefinir sua senha na COE", link -> """
                    Olá!

                    Recebemos um pedido para redefinir a senha da sua conta na COE. Para criar uma senha nova,
                    abra o link abaixo (ele vale por 30 minutos):

                    %s

                    Se não foi você quem pediu, é só ignorar este e-mail: sua senha continua a mesma.

                    Equipe COE
                    """.formatted(link));

    private final UsuarioRepository usuarios;
    private final ServicoDeTokens tokens;
    private final ServicoDeCodigoSms codigos;
    private final CodigoSmsRepository codigosSms;
    private final TokenVerificacaoRepository links;
    private final RefreshTokenRepository sessoes;
    private final ContaDeQuemChama contaDeQuemChama;
    private final PasswordEncoder codificador;
    private final TransactionTemplate transacao;
    private final Clock clock;

    @SuppressWarnings("java:S107") // os três fluxos da senha tocam conta, códigos, links e sessões
    ServicoDeSenha(
            UsuarioRepository usuarios,
            ServicoDeTokens tokens,
            ServicoDeCodigoSms codigos,
            CodigoSmsRepository codigosSms,
            TokenVerificacaoRepository links,
            RefreshTokenRepository sessoes,
            ContaDeQuemChama contaDeQuemChama,
            PasswordEncoder codificador,
            PlatformTransactionManager transacoes,
            Clock clock) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codigos = codigos;
        this.codigosSms = codigosSms;
        this.links = links;
        this.sessoes = sessoes;
        this.contaDeQuemChama = contaDeQuemChama;
        this.codificador = codificador;
        this.transacao = new TransactionTemplate(transacoes);
        this.clock = clock;
    }

    void esqueci(String login, String ip) {
        Optional<Usuario> encontrada = usuarios.buscarPeloLogin(login).filter(Usuario::podeEntrar);
        if (encontrada.isEmpty()) {
            return;
        }
        Usuario conta = encontrada.get();
        if (conta.isEmailConfirmado()) {
            tokens.enviarLink(
                    conta.getId(), conta.getEmail(), FinalidadeToken.RECUPERAR_SENHA, VALIDADE_DO_LINK, EMAIL);
        } else if (conta.isCelularConfirmado() && !codigos.bloqueado(conta.getId())) {
            codigos.enviar(conta.getId(), conta.getCelular(), FinalidadeSms.RECUPERAR_SENHA, ip);
        } else {
            LOG.info("Esqueci a senha sem contato confirmado (suporte): {}", conta.getId());
        }
    }

    void redefinir(RedefinirSenhaRequest pedido) {
        if (pedido.token() != null) {
            redefinirComLink(pedido.token(), pedido.novaSenha());
        } else if (pedido.celular() != null && pedido.codigo() != null) {
            redefinirComCodigo(pedido.celular(), pedido.codigo(), pedido.novaSenha());
        } else {
            throw linkInvalido();
        }
    }

    private void redefinirComLink(String token, String novaSenha) {
        Usuario conta = tokens.espiar(token, FinalidadeToken.RECUPERAR_SENHA)
                .flatMap(link -> usuarios.findById(link.getUsuarioId())
                        .filter(Usuario::podeEntrar)
                        .filter(Usuario::isEmailConfirmado)
                        .filter(dono -> link.getDestino().equals(dono.getEmail())))
                .orElseThrow(ServicoDeSenha::linkInvalido);
        exigirSenhaPermitida(conta, novaSenha);
        String hash = codificador.encode(novaSenha);
        Boolean trocou = transacao.execute(status -> {
            boolean usou = tokens.consumir(
                            token,
                            FinalidadeToken.RECUPERAR_SENHA,
                            link -> link.getUsuarioId().equals(conta.getId()))
                    .isPresent();
            // Confere de novo dentro da transação: suspensa, e-mail trocado ou reivindicado no meio = não vale.
            boolean aindaVale = usou
                    && usuarios.findById(conta.getId())
                            .filter(Usuario::podeEntrar)
                            .filter(Usuario::isEmailConfirmado)
                            .filter(atual -> conta.getEmail().equals(atual.getEmail()))
                            .isPresent();
            if (!aindaVale) {
                status.setRollbackOnly();
                return false;
            }
            gravarSenhaNova(conta.getId(), hash);
            return true;
        });
        if (!Boolean.TRUE.equals(trocou)) {
            throw linkInvalido();
        }
    }

    private void redefinirComCodigo(String digitado, String codigo, String novaSenha) {
        if (!Contato.celularDigitadoValido(digitado)) {
            throw linkInvalido();
        }
        String celular = Contato.normalizarCelular(digitado);
        // Primeiro a parte da regra que não depende da conta: a mesma resposta com e sem conta.
        exigirSenhaPermitida(novaSenha, celular, null);
        Usuario conta = usuarios.findByCelular(celular)
                .filter(Usuario::podeEntrar)
                .filter(Usuario::isCelularConfirmado)
                .filter(dono -> !codigos.bloqueado(dono.getId()))
                .orElse(null);
        if (conta == null || !codigos.conferir(celular, FinalidadeSms.RECUPERAR_SENHA, codigo)) {
            throw linkInvalido();
        }
        // Só com o código certo: o resto da regra (e-mail da conta) e o BCrypt.
        exigirSenhaPermitida(conta, novaSenha);
        String hash = codificador.encode(novaSenha);
        Boolean trocou = transacao.execute(status -> {
            boolean aindaVale = usuarios.findById(conta.getId())
                    .filter(Usuario::podeEntrar)
                    .filter(atual -> celular.equals(atual.getCelular()))
                    .isPresent();
            if (aindaVale) {
                gravarSenhaNova(conta.getId(), hash);
            }
            return aindaVale;
        });
        if (!Boolean.TRUE.equals(trocou)) {
            throw linkInvalido();
        }
    }

    /** Senha nova: nenhum outro link ou código de senha vale e todas as sessões caem. */
    private void gravarSenhaNova(UUID usuarioId, String hash) {
        Instant agora = clock.instant();
        usuarios.redefinirSenhaHash(usuarioId, hash);
        links.invalidarAtivosDaConta(usuarioId, FinalidadeToken.RECUPERAR_SENHA.valor(), agora);
        codigosSms.invalidarAtivosDaConta(usuarioId, FinalidadeSms.RECUPERAR_SENHA.valor(), agora);
        sessoes.revogarDoUsuario(usuarioId, agora, MOTIVO);
        LOG.info("Senha redefinida, todas as sessões encerradas: {}", usuarioId);
    }

    /** Logado: confere a senha atual; mantém a sessão deste aparelho (se o cookie veio) e derruba as outras. */
    void trocar(String senhaAtual, String novaSenha, String refreshDoAparelho) {
        Usuario conta = contaDeQuemChama.carregarAtiva();
        boolean confere = senhaAtual.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_SENHA
                && codificador.matches(senhaAtual, conta.getSenhaHash());
        if (!confere) {
            LOG.info("Troca de senha com a senha atual errada: {}", conta.getId());
            throw new RegraDeNegocioException("senha-atual-incorreta", "A senha atual não confere.");
        }
        exigirSenhaPermitida(conta, novaSenha);
        String hash = codificador.encode(novaSenha);
        transacao.executeWithoutResult(status -> {
            if (usuarios.atualizarSenhaHash(conta.getId(), conta.getSenhaHash(), hash) == 0) {
                throw new ConflitoException(
                        "edicao-concorrente", "Sua senha mudou enquanto você trocava. Tente de novo.", null);
            }
            Instant agora = clock.instant();
            Optional<UUID> familiaDoAparelho = TokenDeRenovacao.hash(refreshDoAparelho)
                    .flatMap(sessoes::findByTokenHash)
                    .filter(sessao -> sessao.getUsuarioId().equals(conta.getId()) && !sessao.revogado())
                    .map(RefreshToken::getFamiliaId);
            if (familiaDoAparelho.isPresent()) {
                sessoes.revogarDoUsuarioMenosAFamilia(conta.getId(), familiaDoAparelho.get(), agora, MOTIVO);
            } else {
                sessoes.revogarDoUsuario(conta.getId(), agora, MOTIVO);
            }
            links.invalidarAtivosDaConta(conta.getId(), FinalidadeToken.RECUPERAR_SENHA.valor(), agora);
            codigosSms.invalidarAtivosDaConta(conta.getId(), FinalidadeSms.RECUPERAR_SENHA.valor(), agora);
        });
        LOG.info("Senha trocada, outras sessões encerradas: {}", conta.getId());
    }

    private static void exigirSenhaPermitida(Usuario conta, String novaSenha) {
        exigirSenhaPermitida(novaSenha, conta.getCelular(), conta.getEmail());
    }

    private static void exigirSenhaPermitida(String novaSenha, String celular, String email) {
        SenhaPermitidaValidador.motivoDeRecusa(novaSenha, celular, email).ifPresent(motivo -> {
            throw new RegraDeNegocioException("senha-nao-permitida", motivo);
        });
    }

    private static RegraDeNegocioException linkInvalido() {
        return new RegraDeNegocioException("link-invalido", "Este link ou código não vale mais. Peça um novo.");
    }
}
