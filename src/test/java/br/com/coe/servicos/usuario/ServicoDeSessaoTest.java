package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

/** O teto da sessão vem da configuração: valor absurdo impede a subida. */
class ServicoDeSessaoTest {

    private static ServicoDeSessao comTeto(Duration teto) {
        return new ServicoDeSessao(
                mock(RefreshTokenRepository.class),
                mock(UsuarioRepository.class),
                mock(PlatformTransactionManager.class),
                Clock.systemUTC(),
                teto);
    }

    @ParameterizedTest(name = "recusa {0}")
    @ValueSource(strings = {"PT0S", "-PT1H", "PT23H", "P366D"})
    void recusaTetoAbsurdo(String teto) {
        assertThatThrownBy(() -> comTeto(Duration.parse(teto)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.auth.sessao.duracao-maxima");
    }

    @ParameterizedTest(name = "aceita {0}")
    @ValueSource(strings = {"P1D", "P90D", "P365D"})
    void aceitaTetoRazoavel(String teto) {
        assertThatCode(() -> comTeto(Duration.parse(teto))).doesNotThrowAnyException();
    }
}
