package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token recebido no fragmento do link (o front lê e manda no corpo). */
record TokenRequest(
        @NotBlank(message = "falta o token") @Size(max = 64, message = "token inválido")
        String token) {

    @Override
    public String toString() {
        return "TokenRequest[token=***]";
    }
}
