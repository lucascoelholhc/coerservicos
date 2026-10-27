package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Relógio deslocado só no perfil local (M3: viver o dia da diária e as 12 h da liberação sem esperar). */
class RelogioLocalTest {

    @Test
    @DisplayName("sem deslocamento, o relógio local é o do sistema")
    void semDeslocamento() {
        Clock relogio = new RelogioLocalConfig().relogioLocal(Duration.ZERO);
        assertThat(Duration.between(Instant.now(), relogio.instant()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("com deslocamento, o relógio anda à frente do sistema")
    void comDeslocamento() {
        Clock relogio = new RelogioLocalConfig().relogioLocal(Duration.ofHours(13));
        assertThat(Duration.between(Instant.now(), relogio.instant()))
                .isBetween(
                        Duration.ofHours(13).minusSeconds(5),
                        Duration.ofHours(13).plusSeconds(5));
    }

    @Test
    @DisplayName("deslocamento para trás é recusado")
    void paraTrasRecusado() {
        assertThatThrownBy(() -> new RelogioLocalConfig().relogioLocal(Duration.ofHours(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deslocamento fora do perfil local (ou junto do prod) não deixa a aplicação subir")
    void foraDoLocalNaoSobe() {
        MockEnvironment prod = new MockEnvironment().withProperty("coe.relogio.deslocamento", "PT1H");
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ConferenciaDoRelogio(prod).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.relogio.deslocamento");

        MockEnvironment misturado = new MockEnvironment().withProperty("coe.relogio.deslocamento", "PT1H");
        misturado.setActiveProfiles("local", "prod");
        assertThatThrownBy(() -> new ConferenciaDoRelogio(misturado).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment local = new MockEnvironment().withProperty("coe.relogio.deslocamento", "PT1H");
        local.setActiveProfiles("local");
        new ConferenciaDoRelogio(local).afterSingletonsInstantiated();

        MockEnvironment semNada = new MockEnvironment();
        semNada.setActiveProfiles("prod");
        new ConferenciaDoRelogio(semNada).afterSingletonsInstantiated();
    }
}
