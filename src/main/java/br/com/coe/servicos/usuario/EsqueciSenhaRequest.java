package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Esqueci a senha: o login (celular ou e-mail). */
record EsqueciSenhaRequest(
        @NotBlank(message = "digite o celular ou o e-mail") @Size(max = 254)
        String login) {

    @Override
    public String toString() {
        return "EsqueciSenhaRequest[login=***]";
    }
}
