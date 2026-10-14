package br.com.coe.servicos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.coe.servicos.compartilhado.seguranca.ContatoPendente;

/** Interceptores do MVC: bloqueio de quem está com contato pendente (RN61) e limite por usuário (CORE-07). */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoMvc implements WebMvcConfigurer {

    private final ContatoPendente contatoPendente;
    private final br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa limitador;

    ConfiguracaoMvc(
            ContatoPendente contatoPendente, br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa limitador) {
        this.contatoPendente = contatoPendente;
        this.limitador = limitador;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new BloqueioDeContatoPendente(contatoPendente));
        registro.addInterceptor(new LimitePorUsuario(limitador));
    }
}
