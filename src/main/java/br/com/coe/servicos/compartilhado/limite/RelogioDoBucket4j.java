package br.com.coe.servicos.compartilhado.limite;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import io.github.bucket4j.TimeMeter;

/** O Bucket4j conta o tempo pelo Clock da aplicação (testes avançam o relógio; ArchUnit do CORE-13). */
final class RelogioDoBucket4j implements TimeMeter {

    private final Clock clock;

    RelogioDoBucket4j(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long currentTimeNanos() {
        return TimeUnit.MILLISECONDS.toNanos(clock.millis());
    }

    @Override
    public boolean isWallClockBased() {
        return true;
    }
}
