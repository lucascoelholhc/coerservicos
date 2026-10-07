package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Provedores falsos de SMS (e, no 7b, de e-mail) para o perfil test. */
@TestConfiguration(proxyBeanMethods = false)
public class MensageriaDeTesteConfiguration {

    /** O despacho é resolvido só na leitura (ele depende deste fake; sem ciclo na criação). */
    @Bean
    EnviadorSmsEmMemoria enviadorSmsEmMemoria(ObjectProvider<DespachoDeMensagens> despacho) {
        return new EnviadorSmsEmMemoria(() -> despacho.getObject().pendentes());
    }
}
