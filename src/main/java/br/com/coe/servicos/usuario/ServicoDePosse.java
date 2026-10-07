package br.com.coe.servicos.usuario;

import java.time.Duration;

import org.springframework.stereotype.Service;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Provar a posse de um celular ou e-mail que está em outra conta sem confirmação (RN61). Só envia
 * quando outra conta tem o dado não confirmado; senão, o mesmo 202 silencioso (não revela contas
 * nem vira bomba de SMS). Certo devolve um comprovante de 30 min para o cadastro.
 */
@Service
class ServicoDePosse {

    static final String MENSAGEM = "Se este contato estiver em outra conta sem confirmação, enviamos a confirmação.";
    static final Duration VALIDADE_DO_LINK = Duration.ofHours(1);

    private static final ServicoDeTokens.Email EMAIL = new ServicoDeTokens.Email(
            "/provar-email", "Confirme que este e-mail é seu na COE", link -> """
                    Olá!

                    Alguém está criando uma conta na COE com este e-mail. Se foi você, abra o link abaixo
                    (ele vale por 1 hora) para confirmar que o e-mail é seu:

                    %s

                    Se não foi você, é só ignorar este e-mail.

                    Equipe COE
                    """.formatted(link));

    private final UsuarioRepository usuarios;
    private final ServicoDeCodigoSms codigos;
    private final ServicoDeTokens tokens;

    ServicoDePosse(UsuarioRepository usuarios, ServicoDeCodigoSms codigos, ServicoDeTokens tokens) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.tokens = tokens;
    }

    void pedirPeloCelular(String digitado, String ip) {
        if (!Contato.celularDigitadoValido(digitado)) {
            return;
        }
        String celular = Contato.normalizarCelular(digitado);
        boolean reivindicavel = usuarios.findByCelular(celular)
                .filter(conta -> !conta.isCelularConfirmado())
                .isPresent();
        if (reivindicavel && !codigos.bloqueadoParaPosse(celular)) {
            codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, ip);
        }
    }

    String confirmarCelular(String digitado, String codigo) {
        String celular = Contato.normalizarCelular(digitado);
        if (!Contato.celularDigitadoValido(digitado)
                || codigos.bloqueadoParaPosse(celular)
                || !codigos.conferir(celular, FinalidadeSms.COMPROVAR_POSSE, codigo)) {
            throw new RegraDeNegocioException("codigo-invalido", "Código incorreto ou vencido. Peça um novo.");
        }
        return tokens.emitirComprovante(TokenVerificacao.CANAL_CELULAR, celular);
    }

    void pedirPeloEmail(String digitado) {
        String email = Contato.normalizarEmail(digitado);
        if (!Contato.emailValido(email)) {
            return;
        }
        boolean reivindicavel = usuarios.findByEmail(email)
                .filter(conta -> !conta.isEmailConfirmado())
                .isPresent();
        if (reivindicavel) {
            tokens.enviarLink(null, email, FinalidadeToken.LINK_POSSE, VALIDADE_DO_LINK, EMAIL);
        }
    }

    String confirmarEmail(String token) {
        return tokens.consumir(token, FinalidadeToken.LINK_POSSE)
                .map(link -> tokens.emitirComprovante(TokenVerificacao.CANAL_EMAIL, link.getDestino()))
                .orElseThrow(
                        () -> new RegraDeNegocioException("link-invalido", "Este link não vale mais. Peça um novo."));
    }
}
