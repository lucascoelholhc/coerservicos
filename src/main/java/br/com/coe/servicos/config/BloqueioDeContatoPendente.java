package br.com.coe.servicos.config;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.seguranca.ContatoPendente;
import br.com.coe.servicos.compartilhado.seguranca.LiberadoComContatoPendente;
import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Contato pendente (RN61): nega por padrão (403 contato-pendente); só passa endpoint
 * {@link LiberadoComContatoPendente} ou {@link Publico}. Vale a marca do token OU o banco: o token
 * emitido antes da transferência não tem a marca e ainda vale até 15 min. Bloqueia começar coisas
 * novas, nunca a execução do que já foi pago (no DOM-08, os endpoints de execução são liberados).
 */
class BloqueioDeContatoPendente implements HandlerInterceptor {

    private final ContatoPendente contatoPendente;

    BloqueioDeContatoPendente(ContatoPendente contatoPendente) {
        this.contatoPendente = contatoPendente;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object alvo) {
        if (alvo instanceof HandlerMethod metodo && !liberado(metodo) && pendente()) {
            throw new AcaoProibidaException(
                    "contato-pendente", "Cadastre e confirme um novo celular ou e-mail para continuar usando a COE.");
        }
        return true;
    }

    private boolean pendente() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            return false;
        }
        if (Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(ConfiguracaoJwt.CLAIM_CONTATO_PENDENTE))) {
            return true;
        }
        return contatoPendente.pendente(UUID.fromString(token.getToken().getSubject()));
    }

    private static boolean liberado(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(LiberadoComContatoPendente.class)
                || metodo.getBeanType().isAnnotationPresent(LiberadoComContatoPendente.class)
                || metodo.hasMethodAnnotation(Publico.class);
    }
}
