package br.com.coe.servicos.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FusoDeNegocioTest {

    @Test
    @DisplayName("o fuso de negócio é o de São Paulo")
    void fusoDeSaoPaulo() {
        assertThat(FusoDeNegocio.ZONA).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    @DisplayName("hoje() usa o fuso de negócio: 02:00 UTC ainda é o dia anterior no Brasil")
    void hojeNoFusoDeNegocio() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T02:00:00Z"), ZoneOffset.UTC);

        assertThat(FusoDeNegocio.hoje(clock)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("hoje() vira o dia à meia-noite de São Paulo (03:00 UTC)")
    void viraODiaAMeiaNoiteDeSaoPaulo() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneOffset.UTC);

        assertThat(FusoDeNegocio.hoje(clock)).isEqualTo(LocalDate.of(2026, 10, 6));
    }
}
