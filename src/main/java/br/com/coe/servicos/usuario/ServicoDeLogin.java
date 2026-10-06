package br.com.coe.servicos.usuario;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.NaoAutenticadoException;

/**
 * Login com celular ou e-mail + senha (CORE-03; RF01, RN58, PA03), segundo passo por SMS (MFA,
 * CORE-04) e login só com código SMS (RF01). Toda falha de credencial dá o mesmo 401, no mesmo tempo
 * (login inexistente também passa pelo BCrypt, com um hash fictício). O BCrypt roda fora de
 * transação; só a gravação da sessão é transacional.
 */
@Service
class ServicoDeLogin {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeLogin.class);
    private static final int MAXIMO_BYTES_SENHA = 72; // o BCrypt recusa mais que isso
    /** Mesma resposta para qualquer celular (existe ou não, recebeu ou não): não revela contas. */
    static final String MENSAGEM_CODIGO_ENVIADO = "Se o celular tiver conta, enviamos um código por SMS.";

    record LoginAceito(UsuarioResumo usuario, RefreshEmitido refresh) {}

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final ServicoDeSessao sessoes;
    private final ServicoDeCodigoSms codigos;
    private final String hashFicticio;

    ServicoDeLogin(
            UsuarioRepository usuarios,
            PasswordEncoder codificador,
            ServicoDeSessao sessoes,
            ServicoDeCodigoSms codigos) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.sessoes = sessoes;
        this.codigos = codigos;
        this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
    }

    LoginAceito entrar(LoginRequest pedido, String ip, String userAgent) {
        Optional<Usuario> encontrado = buscar(pedido.login());
        boolean senhaConfere = conferir(encontrado, pedido.senha());
        Usuario usuario = encontrado
                .filter(conta -> senhaConfere)
                .filter(conta -> !Usuario.EXCLUIDO.equals(conta.getStatus()))
                .orElseThrow(() -> {
                    LOG.info("Login recusado");
                    return new NaoAutenticadoException("login-invalido", "Login ou senha incorretos.");
                });
        if (Usuario.SUSPENSO.equals(usuario.getStatus())) {
            LOG.info("Login recusado (conta suspensa): {}", usuario.getId());
            throw new AcaoProibidaException("conta-suspensa", "Sua conta está suspensa. Fale com a equipe da COE.");
        }
        if (precisaSegundoPasso(usuario)) {
            throw pedirSegundoPasso(usuario, ip);
        }
        String hashNovo =
                codificador.upgradeEncoding(usuario.getSenhaHash()) ? codificador.encode(pedido.senha()) : null;
        return abrirSessao(usuario, hashNovo, ip, userAgent);
    }

    /** Segundo passo: o desafio do 403 + o código do SMS. Qualquer falha dá o 401 genérico. */
    LoginAceito segundoPasso(SegundoPassoRequest pedido, String ip, String userAgent) {
        Usuario usuario = codigos.conferirDesafio(pedido.desafioId(), pedido.codigo())
                .flatMap(usuarios::findById)
                .filter(ServicoDeLogin::podeEntrar)
                .orElseThrow(() -> codigoRecusado("segundo passo"));
        return abrirSessao(usuario, null, ip, userAgent);
    }

    /** Pede o código para entrar sem senha. Só envia a quem pode usar; a resposta é sempre a mesma. */
    void pedirCodigo(String celular, String ip) {
        buscarQuemEntraComCodigo(celular)
                .ifPresent(usuario -> codigos.enviar(usuario.getId(), usuario.getCelular(), FinalidadeSms.LOGIN, ip));
    }

    /** Entrar só com o código SMS: celular confirmado, nunca ADMIN nem quem ligou o MFA. */
    LoginAceito entrarComCodigo(EntrarComCodigoRequest pedido, String ip, String userAgent) {
        Usuario usuario = buscarQuemEntraComCodigo(pedido.celular())
                .filter(conta -> codigos.conferir(conta.getCelular(), FinalidadeSms.LOGIN, pedido.codigo()))
                .orElseThrow(() -> codigoRecusado("login por código"));
        return abrirSessao(usuario, null, ip, userAgent);
    }

    private LoginAceito abrirSessao(Usuario usuario, String hashNovo, String ip, String userAgent) {
        RefreshEmitido refresh = sessoes.abrir(usuario.getId(), usuario.getSenhaHash(), hashNovo, ip, userAgent);
        LOG.info("Login ok: {}", usuario.getId());
        return new LoginAceito(UsuarioResumo.de(usuario), refresh);
    }

    /** Senha certa e MFA exigido: manda o SMS e responde 403 com o desafio (nenhum token ainda). */
    private RuntimeException pedirSegundoPasso(Usuario usuario, String ip) {
        if (!usuario.isCelularConfirmado()) {
            LOG.info("Login com MFA sem celular confirmado: {}", usuario.getId());
            return new AcaoProibidaException(
                    "celular-nao-confirmado", "Seu celular ainda não foi confirmado. Fale com a equipe da COE.");
        }
        Optional<String> desafio = codigos.emitirDesafio(usuario.getId(), usuario.getCelular(), ip);
        if (desafio.isEmpty()) {
            return new MuitasTentativasException("Você pediu muitos códigos. Aguarde um pouco e tente de novo.");
        }
        LOG.info("Login aguardando o segundo passo: {}", usuario.getId());
        return new AcaoProibidaException(
                "segundo-passo-necessario",
                "Digite o código que enviamos por SMS.",
                Map.of("desafioId", desafio.get()));
    }

    private Optional<Usuario> buscarQuemEntraComCodigo(String celular) {
        if (!Contato.celularDigitadoValido(celular)) {
            return Optional.empty();
        }
        return usuarios.findByCelular(Contato.normalizarCelular(celular))
                .filter(ServicoDeLogin::podeEntrar)
                .filter(Usuario::isCelularConfirmado)
                .filter(conta -> !precisaSegundoPasso(conta));
    }

    private static boolean podeEntrar(Usuario usuario) {
        return !Usuario.EXCLUIDO.equals(usuario.getStatus()) && !Usuario.SUSPENSO.equals(usuario.getStatus());
    }

    private static NaoAutenticadoException codigoRecusado(String etapa) {
        LOG.info("Código recusado ({})", etapa);
        return new NaoAutenticadoException("login-invalido", "Código incorreto ou vencido.");
    }

    /** Pelo formato: com @ é e-mail; senão, celular (normalizado como no cadastro). */
    private Optional<Usuario> buscar(String login) {
        String texto = login.strip();
        if (texto.contains("@")) {
            return usuarios.findByEmail(Contato.normalizarEmail(texto));
        }
        if (Contato.celularDigitadoValido(texto)) {
            return usuarios.findByCelular(Contato.normalizarCelular(texto));
        }
        return Optional.empty();
    }

    private boolean conferir(Optional<Usuario> usuario, String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_SENHA) {
            codificador.matches("senha-fora-do-limite", hashFicticio); // mesmo tempo de resposta
            return false;
        }
        String hash = usuario.map(Usuario::getSenhaHash).orElse(null);
        boolean confere = codificador.matches(senha, hash != null ? hash : hashFicticio);
        return confere && hash != null;
    }

    /** MFA (decisão de 05/10/2026): obrigatório para ADMIN, opcional para os demais. */
    private static boolean precisaSegundoPasso(Usuario usuario) {
        return usuario.isMfaSmsAtivo() || usuario.getPapeis().contains(Papel.ADMIN);
    }
}
