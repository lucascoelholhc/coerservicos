package br.com.coe.servicos.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação (CORE-13). Todo serviço com prazo recebe este {@link Clock} injetado;
 * ler o relógio do sistema direto é proibido (ver ArquiteturaTest). Os testes trocam por um relógio
 * controlável.
 */
@Configuration(proxyBeanMethods = false)
public class RelogioConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
