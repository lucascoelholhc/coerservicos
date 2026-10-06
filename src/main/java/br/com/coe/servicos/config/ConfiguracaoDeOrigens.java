package br.com.coe.servicos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Origens do front por perfil (local e test: o Vite; prod: o domínio da COE, só https). */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeOrigens {

    @Bean
    OrigensPermitidas origensPermitidas(
            @Value("${coe.seguranca.origens-permitidas:}") String origens,
            @Value("${coe.seguranca.origens-somente-https:false}") boolean somenteHttps) {
        return OrigensPermitidas.de(origens, somenteHttps);
    }
}
