package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Base dos links enviados por e-mail (coe.front.url-base): sem ela boa, a aplicação não sobe. */
class UrlDoFrontTest {

    @Test
    void montaOLinkComOTokenNoFragmento() {
        UrlDoFront url = UrlDoFront.de("https://app.coe.com.br", true);

        assertThat(url.link("/confirmar-email", "abc_DEF-123"))
                .isEqualTo("https://app.coe.com.br/confirmar-email#token=abc_DEF-123");
    }

    @Test
    void localAceitaHttp() {
        assertThat(UrlDoFront.de("http://localhost:5173", false).link("/x", "t"))
                .isEqualTo("http://localhost:5173/x#token=t");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "  ",
                "app.coe.com.br",
                "https://app.coe.com.br/",
                "https://app.coe.com.br/caminho",
                "ftp://coe.com.br"
            })
    void recusaBaseRuim(String base) {
        assertThatThrownBy(() -> UrlDoFront.de(base, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.front.url-base");
    }

    @Test
    void prodExigeHttps() {
        assertThatThrownBy(() -> UrlDoFront.de("http://app.coe.com.br", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }
}
