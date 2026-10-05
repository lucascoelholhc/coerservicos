package br.com.coe.servicos.usuario;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.NaoAutenticadoException;

/**
 * Login com celular ou e-mail + senha (CORE-03; RF01, RN58, PA03). Toda falha de credencial dá o
 * mesmo 401, no mesmo tempo (login inexistente também passa pelo BCrypt, com um hash fictício). O
 * BCrypt roda fora de transação; só a gravação da sessão é transacional.
 */
@Service
class ServicoDeLogin {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoDeLogin.class);
    private static final int MAXIMO_BYTES_SENHA = 72; // o BCrypt recusa mais que isso

    record LoginAceito(UsuarioResumo usuario, String refresh) {}

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final ServicoDeSessao sessoes;
    private final ObjectProvider<DispensaMfaAdminLocal> dispensaLocal;
    private final String hashFicticio;

    ServicoDeLogin(
            UsuarioRepository usuarios,
            PasswordEncoder codificador,
            ServicoDeSessao sessoes,
            ObjectProvider<DispensaMfaAdminLocal> dispensaLocal) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.sessoes = sessoes;
        this.dispensaLocal = dispensaLocal;
        this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
    }

    LoginAceito entrar(LoginRequest pedido, String ip, String userAgent) {
        Optional<Usuario> encontrado = buscar(pedido.login());
        boolean senhaConfere = conferir(encontrado, pedido.senha());
        if (!senhaConfere || Usuario.EXCLUIDO.equals(encontrado.get().getStatus())) {
            LOG.info("Login recusado");
            throw new NaoAutenticadoException("login-invalido", "Login ou senha incorretos.");
        }
        Usuario usuario = encontrado.get();
        if (Usuario.SUSPENSO.equals(usuario.getStatus())) {
            LOG.info("Login recusado (conta suspensa): {}", usuario.getId());
            throw new AcaoProibidaException("conta-suspensa", "Sua conta está suspensa. Fale com a equipe da COE.");
        }
        if (precisaSegundoPasso(usuario)) {
            LOG.info("Login aguardando o segundo passo: {}", usuario.getId());
            throw new AcaoProibidaException(
                    "segundo-passo-necessario", "Confirme o código enviado por SMS para entrar.");
        }
        String novoHash =
                codificador.upgradeEncoding(usuario.getSenhaHash()) ? codificador.encode(pedido.senha()) : null;
        String refresh = sessoes.abrir(usuario.getId(), novoHash, ip, userAgent);
        LOG.info("Login ok: {}", usuario.getId());
        return new LoginAceito(UsuarioResumo.de(usuario), refresh);
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
    private boolean precisaSegundoPasso(Usuario usuario) {
        if (usuario.isMfaSmsAtivo()) {
            return true;
        }
        return usuario.getPapeis().contains(Papel.ADMIN) && dispensaLocal.getIfAvailable() == null;
    }
}
