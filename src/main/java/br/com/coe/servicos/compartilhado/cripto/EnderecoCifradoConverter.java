package br.com.coe.servicos.compartilhado.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Endereço completo cifrado (coluna {@code endereco_cifrado}). Use com {@code @Convert(converter = EnderecoCifradoConverter.class)} na entidade; o Hibernate cria o
 * conversor pelo Spring (injeção pelo construtor).
 */
@Converter
public class EnderecoCifradoConverter implements AttributeConverter<String, byte[]> {

    private final CifraDeCampo cifra;

    public EnderecoCifradoConverter(CifraDeCampo cifra) {
        this.cifra = cifra;
    }

    @Override
    public byte[] convertToDatabaseColumn(String valor) {
        return valor == null ? null : cifra.cifrar(valor, Campo.ENDERECO);
    }

    @Override
    public String convertToEntityAttribute(byte[] coluna) {
        String texto = cifra.decifrar(coluna, Campo.ENDERECO);
        return texto == null ? null : texto;
    }
}
