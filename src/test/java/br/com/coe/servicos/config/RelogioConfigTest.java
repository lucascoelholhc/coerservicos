package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelogioConfigTest {

    @Test
    @DisplayName("o relógio da aplicação é o do sistema, em UTC")
    void relogioEmUtc() {
        assertThat(new RelogioConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
