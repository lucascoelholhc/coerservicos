package br.com.coe.servicos.compartilhado.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/**
 * CPF cifrado (coluna {@code cpf_cifrado}). Use com {@code @Convert(converter = CpfCifradoConverter.class)} na entidade; o Hibernate cria o
 * conversor pelo Spring (injeção pelo construtor).
 */
@Converter
public class CpfCifradoConverter implements AttributeConverter<Cpf, byte[]> {

    private final CifraDeCampo cifra;

    public CpfCifradoConverter(CifraDeCampo cifra) {
        this.cifra = cifra;
    }

    @Override
    public byte[] convertToDatabaseColumn(Cpf valor) {
        return valor == null ? null : cifra.cifrar(valor.digitos(), Campo.CPF);
    }

    @Override
    public Cpf convertToEntityAttribute(byte[] coluna) {
        String texto = cifra.decifrar(coluna, Campo.CPF);
        if (texto == null) {
            return null;
        }
        try {
            return Cpf.de(texto);
        } catch (RegraDeNegocioException invalido) {
            // decifrou mas não é CPF válido: dado corrompido no banco, não erro de quem pediu
            throw new DadoCifradoInvalidoException(Campo.CPF, "o valor decifrado não é um CPF válido");
        }
    }
}
