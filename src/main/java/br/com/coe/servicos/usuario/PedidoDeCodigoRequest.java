package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedir código SMS para entrar sem senha. */
record PedidoDeCodigoRequest(
        @NotBlank(message = "digite o celular") @Size(max = 25, message = "celular inválido")
        String celular) {

    @Override
    public String toString() {
        return "PedidoDeCodigoRequest[celular=***]";
    }
}
