package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Chaves do JWT vêm de variável de ambiente: sem chave boa, a aplicação não sobe. */
class ChavesJwtTest {

    private static final String CHAVE_32 = "64cxK3KGrXaWz4l8l3eTpGwHnDHY9ust4wiQ1nC8+OM=";
    private static final String OUTRA_32 = "wW+XYKypOvCgAc5BG5mlGksqdNbUDiXtlPMh4p43hxE=";
    private static final String CHAVE_16 = "AAECAwQFBgcICQoLDA0ODw==";

    @Test
    @DisplayName("lê a atual e a anterior (rotação)")
    void leAtualEAnterior() {
        ChavesJwt chaves = ChavesJwt.de("k2", "k2:" + CHAVE_32 + ", k1:" + OUTRA_32);

        assertThat(chaves.kidAtual()).isEqualTo("k2");
        assertThat(chaves.todas()).containsOnlyKeys("k2", "k1");
        assertThat(chaves.chaveAtual().getEncoded()).hasSize(32);
    }

    @ParameterizedTest(name = "recusa: {0}")
    @CsvSource(delimiter = '|', textBlock = """
            sem kid atual            |    | k1:64cxK3KGrXaWz4l8l3eTpGwHnDHY9ust4wiQ1nC8+OM=
            sem chaves               | k1 |
            kid atual fora do mapa   | k9 | k1:64cxK3KGrXaWz4l8l3eTpGwHnDHY9ust4wiQ1nC8+OM=
            chave curta (128 bits)   | k1 | k1:AAECAwQFBgcICQoLDA0ODw==
            base64 inválido          | k1 | k1:não-é-base64!!
            sem separador            | k1 | 64cxK3KGrXaWz4l8l3eTpGwHnDHY9ust4wiQ1nC8+OM=
            kid repetido             | k1 | k1:64cxK3KGrXaWz4l8l3eTpGwHnDHY9ust4wiQ1nC8+OM=,k1:wW+XYKypOvCgAc5BG5mlGksqdNbUDiXtlPMh4p43hxE=
            """)
    void recusaConfiguracaoRuim(String caso, String kidAtual, String chaves) {
        assertThatThrownBy(() -> ChavesJwt.de(kidAtual, chaves))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Chaves do JWT")
                .hasMessageNotContaining(CHAVE_32)
                .hasMessageNotContaining(CHAVE_16);
    }

    @Test
    @DisplayName("o toString nunca mostra as chaves")
    void toStringSemSegredo() {
        assertThat(ChavesJwt.de("k1", "k1:" + CHAVE_32).toString()).doesNotContain(CHAVE_32);
        assertThat(new PropriedadesJwt("k1", "k1:" + CHAVE_32).toString()).doesNotContain(CHAVE_32);
    }

    @Test
    @DisplayName("sem as variáveis de ambiente, o contexto não sobe")
    void naoSobeSemChave() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoJwt.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("coe.jwt.kid-atual=", "coe.jwt.chaves=")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasStackTraceContaining("Chaves do JWT");
                });
    }
}
