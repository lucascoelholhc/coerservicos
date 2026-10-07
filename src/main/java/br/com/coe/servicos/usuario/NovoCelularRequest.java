package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;

import br.com.coe.servicos.usuario.validacao.CelularBrasileiro;

/** Celular novo para a conta que perdeu o dela (RN61). */
record NovoCelularRequest(@NotBlank @CelularBrasileiro String celular) {

    @Override
    public String toString() {
        return "NovoCelularRequest[celular=***]";
    }
}
