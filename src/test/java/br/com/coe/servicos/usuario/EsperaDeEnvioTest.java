package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CORE-07: Retry-After dos limites por destino (60 s por finalidade e 5 por hora somando). */
class EsperaDeEnvioTest {

    private static final Instant AGORA = Instant.parse("2026-10-14T12:00:00Z");
    private static final Duration INTERVALO = Duration.ofSeconds(60);

    private static Duration calcular(Optional<Instant> ultimo, List<Instant> daHora) {
        return EsperaDeEnvio.calcular(AGORA, ultimo, daHora, INTERVALO, 5);
    }

    @Test
    @DisplayName("só a espera da finalidade: o que falta dos 60 s")
    void soAFinalidade() {
        assertThat(calcular(Optional.of(AGORA.minusSeconds(20)), List.of(AGORA.minusSeconds(20))))
                .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("só o teto da hora: até o 5º mais novo sair da janela")
    void soOTeto() {
        List<Instant> daHora = List.of(
                AGORA.minusSeconds(100),
                AGORA.minusSeconds(200),
                AGORA.minusSeconds(300),
                AGORA.minusSeconds(400),
                AGORA.minusSeconds(500));

        assertThat(calcular(Optional.empty(), daHora)).isEqualTo(Duration.ofSeconds(3600 - 500));
    }

    @Test
    @DisplayName("os dois: vale a maior espera")
    void osDois() {
        List<Instant> daHora = List.of(
                AGORA.minusSeconds(10),
                AGORA.minusSeconds(3590),
                AGORA.minusSeconds(3591),
                AGORA.minusSeconds(3592),
                AGORA.minusSeconds(3593));

        assertThat(calcular(Optional.of(AGORA.minusSeconds(10)), daHora)).isEqualTo(Duration.ofSeconds(50));
    }

    @Test
    @DisplayName("nada a esperar (corrida com o relógio): 1 s, nunca zero")
    void minimo() {
        assertThat(calcular(Optional.empty(), List.of())).isEqualTo(Duration.ofSeconds(1));
        assertThat(calcular(Optional.of(AGORA.minusSeconds(60)), List.of())).isEqualTo(Duration.ofSeconds(1));
    }
}
