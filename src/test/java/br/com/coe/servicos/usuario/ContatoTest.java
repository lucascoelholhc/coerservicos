package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Normalização dos dados de contato antes de validar e gravar (CORE-02). */
class ContatoTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "47900000101         | 47900000101",
                "(47) 90000-0101     | 47900000101",
                "47 9 0000-0101      | 47900000101",
                "+55 47 90000-0101   | 47900000101", // 13 dígitos: 55 + DDD + 9 dígitos
                "5547900000101       | 47900000101",
                "55 99123-4567       | 55991234567" // 11 dígitos: DDD 55 (Santa Maria/RS), fica como está
            })
    @DisplayName("celular: guarda só os dígitos; tira o 55 do país só quando há 13 dígitos")
    void normalizaCelular(String entrada, String esperado) {
        assertThat(Contato.normalizarCelular(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "válido: {0}")
    @ValueSource(strings = {"47900000101", "55991234567", "11900000000"})
    void celularValido(String celular) {
        assertThat(Contato.celularValido(celular)).isTrue();
    }

    @ParameterizedTest(name = "inválido: {0}")
    @ValueSource(
            strings = {
                "4790000010", // 10 dígitos (fixo)
                "47800000101", // 3º dígito não é 9
                "07900000101", // DDD começando com 0
                "479000001012", // 12 dígitos
                "abc"
            })
    void celularInvalido(String celular) {
        assertThat(Contato.celularValido(Contato.normalizarCelular(celular))).isFalse();
    }

    @Test
    @DisplayName("e-mail: sem espaços nas pontas e em minúsculas")
    void normalizaEmail() {
        assertThat(Contato.normalizarEmail("  Ana.Silva@Exemplo.COM ")).isEqualTo("ana.silva@exemplo.com");
    }

    @Test
    @DisplayName("CEP: só os dígitos")
    void normalizaCep() {
        assertThat(Contato.normalizarCep("89010-000")).isEqualTo("89010000");
        assertThat(Contato.normalizarCep("89010000")).isEqualTo("89010000");
    }

    @Test
    @DisplayName("nome: sem espaços nas pontas")
    void normalizaNome() {
        assertThat(Contato.normalizarNome("  Ana Silva  ")).isEqualTo("Ana Silva");
    }

    @Test
    @DisplayName("nulo continua nulo (a obrigatoriedade é validada à parte)")
    void nulos() {
        assertThat(Contato.normalizarCelular(null)).isNull();
        assertThat(Contato.normalizarEmail(null)).isNull();
        assertThat(Contato.normalizarCep(null)).isNull();
        assertThat(Contato.normalizarNome(null)).isNull();
    }
}
