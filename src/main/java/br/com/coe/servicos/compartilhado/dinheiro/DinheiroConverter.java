package br.com.coe.servicos.compartilhado.dinheiro;

import java.math.BigDecimal;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Grava {@link Dinheiro} em colunas {@code numeric(12,2)}; vale para todo atributo do tipo. */
@Converter(autoApply = true)
public class DinheiroConverter implements AttributeConverter<Dinheiro, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Dinheiro dinheiro) {
        return dinheiro == null ? null : dinheiro.valor();
    }

    @Override
    public Dinheiro convertToEntityAttribute(BigDecimal coluna) {
        return coluna == null ? null : new Dinheiro(coluna);
    }
}
