package br.com.coe.servicos.usuario;

import java.time.Instant;

/** Token de acesso emitido (vale 15 min). Nunca vai para log. */
public record TokenDeAcesso(String valor, Instant expiraEm) {

    @Override
    public String toString() {
        return "TokenDeAcesso[expiraEm=" + expiraEm + "]";
    }
}
