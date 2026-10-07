package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Contador de falhas com valores fora da faixa: a aplicação não sobe. */
class LimitesDoContadorTest {

    @ParameterizedTest
    @CsvSource({"0, PT24H, PT1H", "10, PT0S, PT1H", "10, PT24H, PT0S", "10, -PT1H, PT1H"})
    void recusaValorForaDaFaixa(int falhas, Duration janela, Duration bloqueio) {
        assertThatThrownBy(() ->
                        new ServicoDeCodigoSms(null, null, null, null, Clock.systemUTC(), falhas, janela, bloqueio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.seguranca.codigos");
    }
}
