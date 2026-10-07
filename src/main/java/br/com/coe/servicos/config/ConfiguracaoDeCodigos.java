package br.com.coe.servicos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chave dos códigos SMS: obrigatória (fail fast), vinda de COE_CHAVE_CODIGOS. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeCodigos {

    @Bean
    ChaveDeCodigos chaveDeCodigos(@Value("${coe.seguranca.chave-codigos:}") String chave) {
        return ChaveDeCodigos.de(chave);
    }
}
