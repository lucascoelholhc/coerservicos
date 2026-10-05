package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de login: celular (com ou sem máscara/+55) ou e-mail, e a senha. Os tamanhos máximos só
 * barram abuso; senha acima de 72 bytes não dá 400, cai no mesmo 401 do login errado.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254) String login,
        @NotBlank @Size(max = 1024) String senha) {

    @Override
    public String toString() {
        return "LoginRequest[dados omitidos]";
    }
}
