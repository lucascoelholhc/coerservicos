package br.com.coe.servicos.compartilhado.dinheiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/** Percentual como fração (0,10 = 10%), escala 4 como {@code numeric(5,4)}, sempre 0 <= p < 1. */
@JsonSerialize(using = DinheiroJson.SerializadorPercentual.class)
@JsonDeserialize(using = DinheiroJson.DesserializadorPercentual.class)
public record Percentual(BigDecimal valor) {

    public static final Percentual ZERO = new Percentual(BigDecimal.ZERO);

    private static final int ESCALA = 4;
    private static final int TAMANHO_MAXIMO_TEXTO = 16;

    public Percentual {
        Objects.requireNonNull(valor, "valor");
        // Antes de qualquer setScale: expoente enorme não pode virar número gigante.
        if (valor.precision() - valor.scale() > 1) {
            throw new IllegalArgumentException("Percentual fora de 0 <= p < 1");
        }
        if (valor.stripTrailingZeros().scale() > ESCALA) {
            throw new IllegalArgumentException("Percentual com mais de 4 casas decimais");
        }
        valor = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Percentual fora de 0 <= p < 1: " + valor.toPlainString());
        }
    }

    /** Cria a partir da fração em texto, ex.: "0.10" para 10%. */
    public static Percentual de(String valor) {
        if (valor.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException("Percentual em texto longo demais");
        }
        return new Percentual(new BigDecimal(valor));
    }

    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
