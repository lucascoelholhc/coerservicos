package br.com.coe.servicos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.coe.servicos.compartilhado.limite.Balde;
import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;
import br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa;

/**
 * Cobra o limite {@link LimitadoPorUsuario} com a chave = id do usuário do token (o mesmo que o
 * UsuarioAutenticado usa). Sem saldo, a MuitasTentativasException vira 429 com Retry-After no
 * TratadorDeErros; banco do limitador fora, 503.
 */
class LimitePorUsuario implements HandlerInterceptor {

    /** Sem usuário (não deveria chegar aqui: a rota exige login): todos dividem um balde só. */
    private static final String SEM_USUARIO = "sem-usuario";

    private final LimitadorDeTaxa limitador;

    LimitePorUsuario(LimitadorDeTaxa limitador) {
        this.limitador = limitador;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object alvo) {
        if (alvo instanceof HandlerMethod metodo) {
            LimitadoPorUsuario limite = metodo.getMethodAnnotation(LimitadoPorUsuario.class);
            if (limite != null) {
                limitador.consumir(Balde.porChave(limite.value(), usuario()));
                if (!limite.global().isEmpty()) {
                    limitador.consumir(Balde.global(limite.global()));
                }
            }
        }
        return true;
    }

    private static String usuario() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao == null || autenticacao.getName() == null ? SEM_USUARIO : autenticacao.getName();
    }
}
