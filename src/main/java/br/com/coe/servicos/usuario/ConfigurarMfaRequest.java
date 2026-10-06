package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Ligar ou desligar o código por SMS no login. */
record ConfigurarMfaRequest(
        @NotNull(message = "diga se quer ligar ou desligar") Boolean ativo,

        @NotBlank(message = "digite o código") @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
        String codigo) {

    @Override
    public String toString() {
        return "ConfigurarMfaRequest[ativo=" + ativo + ", codigo=***]";
    }
}
