package br.com.coe.servicos.compartilhado;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fuso das regras de negócio: datas de diária, janela da LC 150, idade mínima e o "dia de hoje"
 * do profissional são contados no horário de São Paulo, mesmo com o relógio da aplicação em UTC.
 */
public final class FusoDeNegocio {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private FusoDeNegocio() {}

    /** O dia de hoje no fuso de negócio, segundo o relógio informado. */
    public static LocalDate hoje(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONA);
    }
}
