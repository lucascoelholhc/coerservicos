package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Origem do bucket no img-src da CSP: por perfil, validada na subida (revisão de segurança do FE-03). */
class OrigemDasImagensTest {

    @Test
    @DisplayName("vazia = só imagens da própria origem (prod até a AWS-04/05)")
    void vazia() {
        assertThat(OrigemDasImagens.validar("", true)).isEmpty();
        assertThat(OrigemDasImagens.validar(null, false)).isEmpty();
        assertThat(SegurancaConfig.politicaDeConteudo(""))
                .contains("img-src 'self' data: blob:; ")
                .doesNotContain("localhost");
    }

    @Test
    @DisplayName("local e test: o SeaweedFS em http://localhost:8333 entra no img-src")
    void local() {
        assertThat(OrigemDasImagens.validar(" http://localhost:8333 ", false)).isEqualTo("http://localhost:8333");
        assertThat(SegurancaConfig.politicaDeConteudo("http://localhost:8333"))
                .contains("img-src 'self' data: blob: http://localhost:8333; ");
    }

    @Test
    @DisplayName("prod: aceita o host https do bucket")
    void producaoHttps() {
        assertThat(OrigemDasImagens.validar("https://coe-arquivos.s3.sa-east-1.amazonaws.com", true))
                .isEqualTo("https://coe-arquivos.s3.sa-east-1.amazonaws.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://bucket.exemplo.com", "https://localhost:8333", "https://127.0.0.1"})
    @DisplayName("prod: http ou localhost não sobe")
    void producaoRecusa(String origem) {
        assertThatThrownBy(() -> OrigemDasImagens.validar(origem, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.seguranca.origem-das-imagens");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://bucket.exemplo.com/caminho",
                "https://*.exemplo.com",
                "https://bucket.exemplo.com; script-src *",
                "'unsafe-inline'",
                "ftp://bucket.exemplo.com",
                "https://bucket.exemplo.com/"
            })
    @DisplayName("só uma origem (esquema + host + porta): nada que mude a política")
    void soOrigem(String origem) {
        assertThatThrownBy(() -> OrigemDasImagens.validar(origem, false)).isInstanceOf(IllegalStateException.class);
    }
}
