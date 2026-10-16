package br.com.coe.servicos.compartilhado.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chave Pix cifrada (colunas {@code chave_pix_cifrada}). Use com {@code @Convert(converter = ChavePixCifradaConverter.class)} na entidade; o Hibernate cria o
 * conversor pelo Spring (injeção pelo construtor).
 */
@Converter
public class ChavePixCifradaConverter implements AttributeConverter<String, byte[]> {

    private final CifraDeCampo cifra;

    public ChavePixCifradaConverter(CifraDeCampo cifra) {
        this.cifra = cifra;
    }

    @Override
    public byte[] convertToDatabaseColumn(String valor) {
        return valor == null ? null : cifra.cifrar(valor, Campo.CHAVE_PIX);
    }

    @Override
    public String convertToEntityAttribute(byte[] coluna) {
        String texto = cifra.decifrar(coluna, Campo.CHAVE_PIX);
        return texto == null ? null : texto;
    }
}
