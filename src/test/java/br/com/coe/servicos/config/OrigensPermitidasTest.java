package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Origens do front (CORS e Origin): configuração ruim impede a subida. */
class OrigensPermitidasTest {

    @Test
    @DisplayName("local: aceita http://localhost:5173 e só ela")
    void local() {
        OrigensPermitidas origens = OrigensPermitidas.de("http://localhost:5173", false);

        assertThat(origens.permite("http://localhost:5173")).isTrue();
        assertThat(origens.permite("http://localhost:5174")).isFalse();
        assertThat(origens.permite("http://localhost:5173/")).isFalse();
        assertThat(origens.permite(null)).isFalse();
        assertThat(origens.lista()).containsExactly("http://localhost:5173");
    }

    @Test
    @DisplayName("prod: aceita https sem caminho, com ou sem porta, várias separadas por vírgula")
    void prod() {
        OrigensPermitidas origens = OrigensPermitidas.de("https://coe.com.br, https://www.coe.com.br:8443", true);

        assertThat(origens.lista()).containsExactly("https://coe.com.br", "https://www.coe.com.br:8443");
    }

    @ParameterizedTest(name = "recusa em qualquer perfil: [{0}]")
    @ValueSource(
            strings = {
                "",
                " ",
                "*",
                "http://localhost:5173/",
                "http://localhost:5173/app",
                "localhost:5173",
                "http://*.coe.com.br"
            })
    void recusaSempre(String origens) {
        assertThatThrownBy(() -> OrigensPermitidas.de(origens, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Origens permitidas inválidas");
    }

    @ParameterizedTest(name = "recusa em prod: [{0}]")
    @ValueSource(
            strings = {"http://coe.com.br", "http://localhost:5173", "https://coe.com.br/", "https://coe.com.br/app"})
    void recusaEmProd(String origens) {
        assertThatThrownBy(() -> OrigensPermitidas.de(origens, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Origens permitidas inválidas");
    }

    @Test
    @DisplayName("sem a configuração, o contexto não sobe")
    void naoSobeSemOrigens() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoDeOrigens.class)
                .withPropertyValues("coe.seguranca.origens-permitidas=")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasStackTraceContaining("Origens permitidas inválidas");
                });
    }

    @Test
    @DisplayName("com origens-somente-https, origem http não sobe")
    void naoSobeComHttpEmProd() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoDeOrigens.class)
                .withPropertyValues(
                        "coe.seguranca.origens-permitidas=http://coe.com.br",
                        "coe.seguranca.origens-somente-https=true")
                .run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    @DisplayName("lista válida sobe")
    void sobe() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoDeOrigens.class)
                .withPropertyValues("coe.seguranca.origens-permitidas=http://localhost:5173")
                .run(contexto -> assertThat(
                                contexto.getBean(OrigensPermitidas.class).lista())
                        .isEqualTo(List.of("http://localhost:5173")));
    }
}
