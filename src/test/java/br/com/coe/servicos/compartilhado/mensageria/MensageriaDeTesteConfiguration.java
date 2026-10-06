package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Provedores falsos de SMS (e, no 7b, de e-mail) para o perfil test. */
@TestConfiguration(proxyBeanMethods = false)
public class MensageriaDeTesteConfiguration {

    @Bean
    EnviadorSmsEmMemoria enviadorSmsEmMemoria() {
        return new EnviadorSmsEmMemoria();
    }
}
