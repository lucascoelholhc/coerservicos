package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;

import br.com.coe.servicos.usuario.validacao.EmailValido;

/** E-mail novo para a conta que perdeu o dela (RN61). */
record NovoEmailRequest(@NotBlank @EmailValido String email) {

    @Override
    public String toString() {
        return "NovoEmailRequest[email=***]";
    }
}
