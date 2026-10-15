package br.com.coe.servicos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.unit.DataSize;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa;
import br.com.coe.servicos.compartilhado.limite.LimitadorEmMemoria;

/**
 * Filtros de entrada (CORE-07), antes do Spring Security (ordem -100): primeiro os limites por IP (não
 * leem o corpo), depois o tamanho do corpo. Assim uma inundação é recusada antes do BCrypt, do JWT e da
 * leitura do JSON.
 */
@Configuration
class ConfiguracaoDosFiltrosDeEntrada {

    /** O primeiro de todos: o id da requisição existe antes de qualquer resposta de erro (CORE-11). */
    @Bean
    FilterRegistrationBean<FiltroDeCorrelacao> filtroDeCorrelacao() {
        FilterRegistrationBean<FiltroDeCorrelacao> registro = new FilterRegistrationBean<>(new FiltroDeCorrelacao());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    @Bean
    FilterRegistrationBean<FiltroDeLimites> filtroDeLimites(
            LimitadorDeTaxa limitador, LimitadorEmMemoria memoria, JsonMapper json) {
        FilterRegistrationBean<FiltroDeLimites> registro =
                new FilterRegistrationBean<>(new FiltroDeLimites(limitador, memoria, json));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registro;
    }

    @Bean
    FilterRegistrationBean<FiltroDeTamanhoDoCorpo> filtroDeTamanhoDoCorpo(
            @Value("${coe.limites.corpo-maximo:64KB}") DataSize maximo, JsonMapper json) {
        FilterRegistrationBean<FiltroDeTamanhoDoCorpo> registro =
                new FilterRegistrationBean<>(new FiltroDeTamanhoDoCorpo(maximo.toBytes(), json));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registro;
    }
}
