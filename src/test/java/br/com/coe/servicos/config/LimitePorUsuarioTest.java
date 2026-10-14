package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.limite.Balde;
import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;
import br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa;

/**
 * CORE-07 (revisão de segurança, H2): numa rota com limite por usuário e global, o do usuário é cobrado
 * antes; quem já passou do próprio limite não gasta o global (uma conta grátis não esgota o global de
 * todo mundo).
 */
class LimitePorUsuarioTest {

    static class Controller {
        @LimitadoPorUsuario(value = "envio-usuario", global = "envio-normal-global")
        void enviar() {}
    }

    private final LimitadorDeTaxa limitador = mock(LimitadorDeTaxa.class);

    private HandlerMethod rota() throws NoSuchMethodException {
        return new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("enviar"));
    }

    @BeforeEach
    void logado() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("usuario-1", null));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("cobra o do usuário e depois o global")
    void ordem() throws Exception {
        new LimitePorUsuario(limitador).preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), rota());

        InOrder ordem = inOrder(limitador);
        ordem.verify(limitador).consumir(Balde.porChave("envio-usuario", "usuario-1"));
        ordem.verify(limitador).consumir(Balde.global("envio-normal-global"));
    }

    @Test
    @DisplayName("sem saldo no do usuário: 429 sem tocar no global")
    void usuarioSemSaldoNaoGastaOGlobal() throws Exception {
        doThrow(new MuitasTentativasException("Muitas tentativas.", Duration.ofMinutes(5)))
                .when(limitador)
                .consumir(Balde.porChave("envio-usuario", "usuario-1"));

        assertThatThrownBy(() -> new LimitePorUsuario(limitador)
                        .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), rota()))
                .isInstanceOf(MuitasTentativasException.class);
        verify(limitador, never()).consumir(Balde.global("envio-normal-global"));
    }
}
