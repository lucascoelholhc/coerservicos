package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PercentualTest {

    @Test
    @DisplayName("normaliza para 4 casas, como numeric(5,4)")
    void normaliza() {
        assertThat(Percentual.de("0.1")).isEqualTo(Percentual.de("0.1000"));
        assertThat(Percentual.de("0.1").valor()).hasScaleOf(4);
        assertThat(Percentual.de("0.1")).hasToString("0.1000");
        assertThat(Percentual.ZERO).isEqualTo(Percentual.de("0"));
    }

    @ParameterizedTest(name = "aceita {0}")
    @ValueSource(strings = {"0", "0.1000", "0.9999"})
    void aceitaFaixa(String valor) {
        assertThat(Percentual.de(valor).valor())
                .isBetween(Percentual.ZERO.valor(), Percentual.de("0.9999").valor());
    }

    @ParameterizedTest(name = "recusa {0}")
    @ValueSource(strings = {"1", "1.00", "-0.0001", "0.12345"})
    void recusaForaDaFaixaOuComMaisCasas(String valor) {
        assertThatIllegalArgumentException().isThrownBy(() -> Percentual.de(valor));
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("expoente enorme e texto longo são recusados na hora")
    void entradaAbusiva() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percentual.de("1E+999999999"));
        assertThatIllegalArgumentException().isThrownBy(() -> Percentual.de("0." + "1".repeat(40)));
    }

    @Test
    @DisplayName("nunca é nulo")
    void nuncaNulo() {
        assertThatNullPointerException().isThrownBy(() -> new Percentual(null));
    }
}
