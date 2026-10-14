package br.com.coe.servicos.compartilhado.erro;

import java.time.Duration;
import java.util.Objects;

/**
 * Limite de pedidos atingido (HTTP 429), por exemplo SMS pedido antes de 60 s. Leva a espera até a
 * próxima tentativa valer, que vira o header {@code Retry-After} (em segundos, arredondado para cima).
 */
public class MuitasTentativasException extends RuntimeException {

    private final Duration espera;

    public MuitasTentativasException(String mensagem, Duration espera) {
        super(mensagem);
        this.espera = Objects.requireNonNull(espera, "espera");
    }

    public Duration espera() {
        return espera;
    }

    /** Segundos para o {@code Retry-After}: arredonda para cima e nunca menos de 1. */
    public long esperaEmSegundos() {
        long segundos = espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}
