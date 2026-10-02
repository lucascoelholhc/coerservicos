package br.com.coe.servicos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Nos testes de integração, quem pede um {@link Clock} recebe o relógio controlável. */
class RelogioIntegracaoTest extends IntegracaoTest {

    @Autowired
    Clock clock;

    @Test
    @DisplayName("o Clock injetado é o relógio de teste, parado no instante padrão e avançável")
    void clockInjetadoEhOAjustavel() {
        assertThat(clock.instant()).isEqualTo(RelogioAjustavel.INSTANTE_PADRAO);

        relogio.avancar(Duration.ofHours(12));

        assertThat(clock.instant()).isEqualTo(RelogioAjustavel.INSTANTE_PADRAO.plus(Duration.ofHours(12)));
    }
}
