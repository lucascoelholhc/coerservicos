package br.com.coe.servicos.usuario;

import java.time.Instant;

/** Resposta do login e da renovação. O refresh vai só no cookie HttpOnly, nunca no corpo. */
public record SessaoResponse(String accessToken, Instant expiraEm, UsuarioResumo usuario) {

    @Override
    public String toString() {
        return "SessaoResponse[usuario=" + usuario.id() + "]";
    }
}
