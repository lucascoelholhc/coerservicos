package br.com.coe.servicos.usuario;

import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Component;

import br.com.coe.servicos.config.ChaveDeCodigos;

/**
 * Chave de balde do rate limit (CORE-07) para login e celular: HMAC com a chave dos códigos, nunca o
 * dado em claro na limite_balde. O login é normalizado como no login de verdade (celular com ou sem
 * máscara, e-mail com espaço ou maiúscula caem no mesmo balde).
 */
@Component
class ChaveDeLimite {

    private static final String PREFIXO = "limite:";

    private final ChaveDeCodigos chave;

    ChaveDeLimite(ChaveDeCodigos chave) {
        this.chave = chave;
    }

    String doLogin(String login) {
        String texto = login == null ? "" : login.strip();
        if (texto.contains("@")) {
            return hmac(Contato.normalizarEmail(texto));
        }
        if (Contato.celularDigitadoValido(texto)) {
            return hmac(Contato.normalizarCelular(texto));
        }
        return hmac(texto.toLowerCase(Locale.ROOT));
    }

    String doCelular(String celular) {
        String texto = celular == null ? "" : celular.strip();
        return hmac(Contato.celularDigitadoValido(texto) ? Contato.normalizarCelular(texto) : texto);
    }

    private String hmac(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(chave.hmac(PREFIXO + texto));
    }
}
