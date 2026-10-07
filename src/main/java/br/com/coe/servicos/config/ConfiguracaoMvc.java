package br.com.coe.servicos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Interceptores do MVC: bloqueio de quem está com contato pendente (RN61). */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoMvc implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new BloqueioDeContatoPendente());
    }
}
