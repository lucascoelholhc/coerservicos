package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Trocar a senha logado: a atual e a nova. */
record TrocarSenhaRequest(
        @NotBlank(message = "digite a senha atual") @Size(max = 200)
        String senhaAtual,

        @NotBlank(message = "digite a senha nova") @Size(max = 200)
        String novaSenha) {

    @Override
    public String toString() {
        return "TrocarSenhaRequest[dados omitidos]";
    }
}
