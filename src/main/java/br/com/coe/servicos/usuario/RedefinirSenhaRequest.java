package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Redefinir a senha: pelo token do link OU pelo celular + código do SMS. */
record RedefinirSenhaRequest(
        @Size(max = 64) String token,
        @Size(max = 25) String celular,

        @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
        String codigo,

        @NotBlank(message = "digite a senha nova") @Size(max = 200)
        String novaSenha) {

    @Override
    public String toString() {
        return "RedefinirSenhaRequest[dados omitidos]";
    }
}
