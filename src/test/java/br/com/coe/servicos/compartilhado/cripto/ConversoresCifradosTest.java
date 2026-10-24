package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/** Conversores JPA: cada campo com o seu AAD (um não decifra o do outro). */
class ConversoresCifradosTest {

    private final CifraDeCampo cifra = new CifraDeCampo(ChavesDeCifra.de("k1", "k1:" + CifraDeCampoTest.CHAVE_1));

    @Test
    @DisplayName("CPF, chave Pix e endereço: ida e volta; nulo continua nulo")
    void idaEVolta() {
        CpfCifradoConverter cpf = new CpfCifradoConverter(cifra);
        ChavePixCifradaConverter pix = new ChavePixCifradaConverter(cifra);
        EnderecoCifradoConverter endereco = new EnderecoCifradoConverter(cifra);

        assertThat(cpf.convertToEntityAttribute(cpf.convertToDatabaseColumn(Cpf.de("52998224725"))))
                .isEqualTo(Cpf.de("52998224725"));
        assertThat(pix.convertToEntityAttribute(pix.convertToDatabaseColumn("pessoa@exemplo.com")))
                .isEqualTo("pessoa@exemplo.com");
        assertThat(endereco.convertToEntityAttribute(endereco.convertToDatabaseColumn("Rua A, 10")))
                .isEqualTo("Rua A, 10");
        assertThat(cpf.convertToDatabaseColumn(null)).isNull();
        assertThat(cpf.convertToEntityAttribute(null)).isNull();
        assertThat(pix.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("CPF que decifra mas não é válido: erro de dado (500), nunca 422 de usuário")
    void cpfCorrompido() {
        byte[] invalido = cifra.cifrar("11111111111", Campo.CPF);

        assertThatThrownBy(() -> new CpfCifradoConverter(cifra).convertToEntityAttribute(invalido))
                .isInstanceOf(DadoCifradoInvalidoException.class)
                .hasMessageNotContaining("11111111111");
    }

    @Test
    @DisplayName("a chave Pix cifrada não decifra como endereço")
    void campoErrado() {
        byte[] pix = new ChavePixCifradaConverter(cifra).convertToDatabaseColumn("pessoa@exemplo.com");

        assertThatThrownBy(() -> new EnderecoCifradoConverter(cifra).convertToEntityAttribute(pix))
                .isInstanceOf(DadoCifradoInvalidoException.class);
    }
}
