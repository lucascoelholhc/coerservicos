package br.com.coe.servicos.compartilhado.dinheiro;

import java.math.BigDecimal;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Grava {@link Percentual} em colunas {@code numeric(5,4)}; vale para todo atributo do tipo. */
@Converter(autoApply = true)
public class PercentualConverter implements AttributeConverter<Percentual, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Percentual percentual) {
        return percentual == null ? null : percentual.valor();
    }

    @Override
    public Percentual convertToEntityAttribute(BigDecimal coluna) {
        return coluna == null ? null : new Percentual(coluna);
    }
}
