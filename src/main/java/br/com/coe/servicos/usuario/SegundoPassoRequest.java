package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Segundo passo do login: o desafio recebido no 403 e o código do SMS. */
record SegundoPassoRequest(
        @NotBlank(message = "falta o desafio") @Size(max = 64, message = "desafio inválido")
        String desafioId,

        @NotBlank(message = "digite o código") @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
        String codigo) {

    @Override
    public String toString() {
        return "SegundoPassoRequest[desafioId=***, codigo=***]";
    }
}
