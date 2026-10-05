package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** O IP e o navegador vão para a prova do aceite (RNF18): nada pode derrubar o cadastro. */
class AceiteTermosTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "127.0.0.1, 127.0.0.1",
        "201.22.10.5, 201.22.10.5",
        "::1, ::1",
        "fe80::1%eth0, fe80::1",
        "2804:14c:5b:1::10%3, 2804:14c:5b:1::10"
    })
    @DisplayName("IP válido é guardado; o scope id do IPv6 (%...) sai, porque o inet do banco recusa")
    void ipValido(String recebido, String guardado) {
        assertThat(AceiteTermos.normalizarIp(recebido)).isEqualTo(guardado);
    }

    @ParameterizedTest(name = "IP descartado: [{0}]")
    @ValueSource(strings = {"", "abc", "exemplo.com.br", "999.1.1.1", "1.2.3", "127.0.0.1; DROP"})
    @DisplayName("IP que não é IP vira nulo (e nunca dispara busca de DNS)")
    void ipInvalido(String recebido) {
        assertThat(AceiteTermos.normalizarIp(recebido)).isNull();
    }

    @Test
    @DisplayName("IP nulo continua nulo")
    void ipNulo() {
        assertThat(AceiteTermos.normalizarIp(null)).isNull();
    }

    @Test
    @DisplayName("navegador sem caractere de controle e com no máximo 500 caracteres")
    void userAgent() {
        assertThat(AceiteTermos.normalizarUserAgent("Teste/1.0\r\nX-Falso: 1\u0000"))
                .isEqualTo("Teste/1.0X-Falso: 1");
        assertThat(AceiteTermos.normalizarUserAgent("a".repeat(600))).hasSize(500);
        assertThat(AceiteTermos.normalizarUserAgent(null)).isNull();
    }
}
