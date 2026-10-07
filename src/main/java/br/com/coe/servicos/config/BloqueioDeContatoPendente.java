package br.com.coe.servicos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.seguranca.LiberadoComContatoPendente;
import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Contato pendente (RN61): com a marca no token, nega por padrão (403 contato-pendente); só passa
 * endpoint {@link LiberadoComContatoPendente} ou {@link Publico}. Bloqueia começar coisas novas,
 * nunca a execução do que já foi pago (no DOM-08, os endpoints de execução recebem a anotação).
 */
class BloqueioDeContatoPendente implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object alvo) {
        if (alvo instanceof HandlerMethod metodo && comMarca() && !liberado(metodo)) {
            throw new AcaoProibidaException(
                    "contato-pendente", "Cadastre e confirme um novo celular ou e-mail para continuar usando a COE.");
        }
        return true;
    }

    private static boolean comMarca() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(ConfiguracaoJwt.CLAIM_CONTATO_PENDENTE));
    }

    private static boolean liberado(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(LiberadoComContatoPendente.class)
                || metodo.getBeanType().isAnnotationPresent(LiberadoComContatoPendente.class)
                || metodo.hasMethodAnnotation(Publico.class);
    }
}
