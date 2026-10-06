package br.com.coe.servicos.usuario;

import java.time.Duration;

/** Refresh em claro (vai só no cookie) e por quanto tempo vale: o Max-Age do cookie é o mesmo. */
record RefreshEmitido(String valor, Duration validade) {

    @Override
    public String toString() {
        return "RefreshEmitido[validade=" + validade + "]";
    }
}
