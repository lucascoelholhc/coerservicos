package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entrar só com o código SMS (sem senha). */
record EntrarComCodigoRequest(
        @NotBlank(message = "digite o celular") @Size(max = 25, message = "celular inválido")
        String celular,

        @NotBlank(message = "digite o código") @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
        String codigo) {

    @Override
    public String toString() {
        return "EntrarComCodigoRequest[celular=***, codigo=***]";
    }
}
