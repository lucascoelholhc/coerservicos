package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Chave do HMAC dos códigos SMS (COE_CHAVE_CODIGOS): sem chave boa, a aplicação não sobe. */
class ChaveDeCodigosTest {

    private static final String CHAVE_32 = "Wm9QbFdGd0x6ZlZ3d2xjR2Y2bVNqN0F1a0Y2cTZhS2k=";

    @Test
    void hmacDe32BytesQueDependeDaChaveEDoTexto() {
        ChaveDeCodigos chave = ChaveDeCodigos.de(CHAVE_32);

        assertThat(chave.hmac("47900000101:login:123456")).hasSize(32);
        assertThat(chave.hmac("47900000101:login:123456")).isEqualTo(chave.hmac("47900000101:login:123456"));
        assertThat(chave.hmac("47900000101:login:123457")).isNotEqualTo(chave.hmac("47900000101:login:123456"));
        assertThat(chave.toString()).doesNotContain(CHAVE_32);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "  ",
                "AAECAwQFBgcICQoLDA0ODw==",
                "não-é-base64!!",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            })
    void recusaChaveRuim(String texto) {
        assertThatThrownBy(() -> ChaveDeCodigos.de(texto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Chave dos códigos inválida")
                .hasMessageContaining("COE_CHAVE_CODIGOS");
    }
}
