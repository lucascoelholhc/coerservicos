package br.com.coe.servicos.compartilhado.pessoal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/** CPF com dígitos verificadores; nunca aparece inteiro em toString. */
class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", " 529 982 247 25 ", "111.444.777-35"})
    @DisplayName("CPF válido, com ou sem máscara, vira só os 11 dígitos")
    void valido(String texto) {
        assertThat(Cpf.de(texto).digitos()).hasSize(11).containsOnlyDigits();
        assertThat(Cpf.de("529.982.247-25").digitos()).isEqualTo("52998224725");
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "11111111111", "00000000000", "1234567890", "123456789012", "abc", ""})
    @DisplayName("dígito errado, sequência repetida ou tamanho errado: 422 cpf-invalido")
    void invalido(String texto) {
        assertThatThrownBy(() -> Cpf.de(texto))
                .isInstanceOfSatisfying(
                        RegraDeNegocioException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("cpf-invalido"))
                .hasMessageNotContaining("529");
    }

    @Test
    @DisplayName("nulo também é inválido")
    void nulo() {
        assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("toString mascarado; igualdade pelos dígitos")
    void mascarado() {
        Cpf cpf = Cpf.de("529.982.247-25");

        assertThat(cpf.toString()).doesNotContain("529982").contains("-25");
        assertThat(cpf).isEqualTo(Cpf.de("52998224725")).hasSameHashCodeAs(Cpf.de("52998224725"));
        assertThat(cpf.formatado()).isEqualTo("529.982.247-25");
    }

    @Test
    @DisplayName("os CPFs de teste são válidos e não se repetem")
    void cpfsDeTeste() {
        assertThat(CpfsDeTeste.unico()).isNotEqualTo(CpfsDeTeste.unico());
    }
}
