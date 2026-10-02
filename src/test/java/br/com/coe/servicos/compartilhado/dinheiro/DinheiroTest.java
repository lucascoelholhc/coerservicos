package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

class DinheiroTest {

    @Test
    @DisplayName("normaliza para 2 casas e compara por valor: 280 = 280.0 = 280.00")
    void igualdadePorValor() {
        assertThat(Dinheiro.de("280")).isEqualTo(Dinheiro.de("280.00"));
        assertThat(new Dinheiro(new BigDecimal("280.0"))).isEqualTo(Dinheiro.de("280.00"));
        assertThat(Dinheiro.de("280").hashCode())
                .isEqualTo(Dinheiro.de("280.00").hashCode());
        assertThat(Dinheiro.de("280").valor()).isEqualByComparingTo("280.00").hasScaleOf(2);
        assertThat(Dinheiro.de("280.00")).hasToString("280.00");
    }

    @Test
    @DisplayName("aceita zeros à direita além de 2 casas, porque não mudam o valor (280.000)")
    void zerosADireita() {
        assertThat(Dinheiro.de("280.000")).isEqualTo(Dinheiro.de("280.00"));
    }

    @Test
    @DisplayName("recusa entrada com mais de 2 casas que mudariam o valor, em vez de arredondar sem avisar")
    void recusaMaisDeDuasCasas() {
        assertThatIllegalArgumentException().isThrownBy(() -> Dinheiro.de("280.005"));
    }

    @Test
    @DisplayName("nunca é nulo")
    void nuncaNulo() {
        assertThatNullPointerException().isThrownBy(() -> new Dinheiro(null));
        assertThatNullPointerException().isThrownBy(() -> Dinheiro.de(null));
    }

    @Test
    @DisplayName("recusa valor negativo com regra de negócio")
    void recusaNegativo() {
        assertThatThrownBy(() -> Dinheiro.de("-0.01"))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).getCodigo())
                .isEqualTo("valor-negativo");
    }

    @Test
    @DisplayName("aceita o teto de numeric(12,2) e recusa um centavo acima")
    void teto() {
        assertThat(Dinheiro.de("9999999999.99").valor()).isEqualByComparingTo(Dinheiro.TETO);
        assertThatThrownBy(() -> Dinheiro.de("10000000000.00"))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).getCodigo())
                .isEqualTo("valor-acima-do-limite");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("expoente enorme é recusado na hora, sem tentar montar o número (evita travar a JVM)")
    void expoenteEnorme() {
        assertThatThrownBy(() -> Dinheiro.de("1E+999999999"))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).getCodigo())
                .isEqualTo("valor-acima-do-limite");
        assertThatThrownBy(() -> Dinheiro.de("-1E+999999999"))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).getCodigo())
                .isEqualTo("valor-negativo");
        assertThatIllegalArgumentException().isThrownBy(() -> Dinheiro.de("1E-999999999"));
    }

    @Test
    @DisplayName("texto longo demais é recusado antes de virar número")
    void textoLongoDemais() {
        assertThatIllegalArgumentException().isThrownBy(() -> Dinheiro.de("1".repeat(40)));
    }

    @Test
    @DisplayName("soma e subtrai")
    void somaESubtrai() {
        assertThat(Dinheiro.de("280.00").somar(Dinheiro.de("28.00"))).isEqualTo(Dinheiro.de("308.00"));
        assertThat(Dinheiro.de("308.00").subtrair(Dinheiro.de("28.00"))).isEqualTo(Dinheiro.de("280.00"));
        assertThat(Dinheiro.de("28.00").subtrair(Dinheiro.de("28.00"))).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    @DisplayName("subtração com resultado negativo é recusada")
    void subtracaoNegativa() {
        assertThatThrownBy(() -> Dinheiro.de("10.00").subtrair(Dinheiro.de("10.01")))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).getCodigo())
                .isEqualTo("valor-negativo");
    }

    @Test
    @DisplayName("soma acima do teto é recusada")
    void somaAcimaDoTeto() {
        assertThatThrownBy(() -> Dinheiro.de("9999999999.99").somar(Dinheiro.de("0.01")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
        "280.00, 0.1000, 28.00",
        "105.05, 0.1000, 10.50", // 10,505 -> 10,50 com HALF_EVEN (HALF_UP daria 10,51)
        "105.15, 0.1000, 10.52", // 10,515 -> 10,52 (vizinho par acima)
        "333.33, 0.1000, 33.33",
        "280.00, 0.0000, 0.00",
        "0.00, 0.1000, 0.00"
    })
    @DisplayName("multiplica por percentual com HALF_EVEN")
    void multiplicaPorPercentual(String valor, String percentual, String esperado) {
        assertThat(Dinheiro.de(valor).multiplicar(Percentual.de(percentual))).isEqualTo(Dinheiro.de(esperado));
    }

    @Test
    @DisplayName("compara valores")
    void compara() {
        assertThat(Dinheiro.de("10.00")).isLessThan(Dinheiro.de("10.01"));
        assertThat(Dinheiro.de("10.00").compareTo(Dinheiro.de("10"))).isZero();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {"1234.56 | R$ 1.234,56", "0 | R$ 0,00", "9999999999.99 | R$ 9.999.999.999,99", "0.5 | R$ 0,50"})
    @DisplayName("formata em pt-BR com espaço comum (não o espaço não separável do NumberFormat)")
    void formataPtBr(String valor, String esperado) {
        assertThat(Dinheiro.de(valor).formatar()).isEqualTo(esperado).doesNotContain("\u00a0", "\u202f");
    }
}
