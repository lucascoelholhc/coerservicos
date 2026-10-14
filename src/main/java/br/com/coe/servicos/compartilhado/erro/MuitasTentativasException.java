package br.com.coe.servicos.compartilhado.erro;

import java.time.Duration;
import java.util.Objects;

/**
 * Limite de pedidos atingido (HTTP 429), por exemplo SMS pedido antes de 60 s. Leva a espera até a
 * próxima tentativa valer, que vira o header {@code Retry-After} (em segundos, arredondado para cima).
 */
public class MuitasTentativasException extends RuntimeException {

    private static final Duration ESPERA_PADRAO = Duration.ofSeconds(60);

    private final Duration espera;

    public MuitasTentativasException(String mensagem, Duration espera) {
        super(mensagem);
        this.espera = Objects.requireNonNull(espera, "espera");
    }

    /** Provisório: sai quando os limites por destino calcularem a espera real (etapa 2c da CORE-07). */
    public MuitasTentativasException(String mensagem) {
        this(mensagem, ESPERA_PADRAO);
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
