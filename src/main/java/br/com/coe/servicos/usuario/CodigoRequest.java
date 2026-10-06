package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Código SMS de 6 dígitos (confirmar celular). */
record CodigoRequest(
        @NotBlank(message = "digite o código") @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
        String codigo) {

    @Override
    public String toString() {
        return "CodigoRequest[codigo=***]";
    }
}
