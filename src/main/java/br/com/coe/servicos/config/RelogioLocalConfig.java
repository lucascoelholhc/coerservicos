package br.com.coe.servicos.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Relógio deslocado para frente, só no perfil local (M3, dia 35): com {@code COE_RELOGIO_DESLOCAMENTO=PT13H} a
 * aplicação vive 13 h no futuro, para testar no navegador o dia da diária e a liberação automática sem esperar. Fora
 * do perfil local a propriedade derruba a subida ({@link ConferenciaDoRelogio}).
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class RelogioLocalConfig {

    @Bean
    @Primary
    Clock relogioLocal(@Value("${coe.relogio.deslocamento:PT0S}") Duration deslocamento) {
        if (deslocamento.isNegative()) {
            throw new IllegalStateException("coe.relogio.deslocamento só anda para frente: " + deslocamento);
        }
        // o relógio do sistema sai só do RelogioConfig (ArquiteturaTest); aqui ele só é deslocado
        Clock sistema = new RelogioConfig().clock();
        return deslocamento.isZero() ? sistema : Clock.offset(sistema, deslocamento);
    }
}
