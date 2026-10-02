package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConversoresJpaTest {

    private final DinheiroConverter dinheiro = new DinheiroConverter();
    private final PercentualConverter percentual = new PercentualConverter();

    @Test
    @DisplayName("Dinheiro vai e volta do numeric(12,2) sem perder centavos")
    void dinheiroIdaEVolta() {
        BigDecimal coluna = dinheiro.convertToDatabaseColumn(Dinheiro.de("1234.56"));

        assertThat(coluna).isEqualByComparingTo("1234.56").hasScaleOf(2);
        assertThat(dinheiro.convertToEntityAttribute(coluna)).isEqualTo(Dinheiro.de("1234.56"));
    }

    @Test
    @DisplayName("Percentual vai e volta do numeric(5,4)")
    void percentualIdaEVolta() {
        BigDecimal coluna = percentual.convertToDatabaseColumn(Percentual.de("0.10"));

        assertThat(coluna).isEqualByComparingTo("0.1000").hasScaleOf(4);
        assertThat(percentual.convertToEntityAttribute(coluna)).isEqualTo(Percentual.de("0.10"));
    }

    @Test
    @DisplayName("coluna nula vira atributo nulo e vice-versa (colunas opcionais)")
    void nulos() {
        assertThat(dinheiro.convertToDatabaseColumn(null)).isNull();
        assertThat(dinheiro.convertToEntityAttribute(null)).isNull();
        assertThat(percentual.convertToDatabaseColumn(null)).isNull();
        assertThat(percentual.convertToEntityAttribute(null)).isNull();
    }
}
