package br.com.coe.servicos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** URL do front para os links por e-mail; em prod (origens só https) exige https. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDoFront {

    @Bean
    UrlDoFront urlDoFront(
            @Value("${coe.front.url-base:}") String base,
            @Value("${coe.seguranca.origens-somente-https:false}") boolean somenteHttps) {
        return UrlDoFront.de(base, somenteHttps);
    }
}
