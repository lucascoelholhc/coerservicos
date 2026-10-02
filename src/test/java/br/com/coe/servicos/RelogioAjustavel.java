package br.com.coe.servicos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio dos testes: parado num instante conhecido e avançado à mão. Substitui o {@link Clock}
 * da aplicação nos testes de integração (ver {@link IntegracaoTest}).
 */
public class RelogioAjustavel extends Clock {

    /** Segunda-feira, 05/10/2026, 9 h em São Paulo. */
    public static final Instant INSTANTE_PADRAO = Instant.parse("2026-10-05T12:00:00Z");

    private volatile Instant agora = INSTANTE_PADRAO;

    public void fixarEm(Instant instante) {
        agora = instante;
    }

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    public void voltarAoPadrao() {
        agora = INSTANTE_PADRAO;
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** Mesmo relógio (avança junto), só com outro fuso. */
    @Override
    public Clock withZone(ZoneId zona) {
        RelogioAjustavel original = this;
        return new Clock() {
            @Override
            public Instant instant() {
                return original.instant();
            }

            @Override
            public ZoneId getZone() {
                return zona;
            }

            @Override
            public Clock withZone(ZoneId outraZona) {
                return original.withZone(outraZona);
            }
        };
    }
}
