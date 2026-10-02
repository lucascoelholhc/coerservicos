package br.com.coe.servicos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Põe o {@link RelogioAjustavel} no lugar do relógio da aplicação nos testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioDeTesteConfiguration {

    @Bean
    @Primary
    RelogioAjustavel relogioAjustavel() {
        return new RelogioAjustavel();
    }
}
