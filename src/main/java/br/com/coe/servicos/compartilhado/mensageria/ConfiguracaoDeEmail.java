package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * E-mail pelo SMTP quando {@code spring.mail.host} existe (Mailpit no local; em prod,
 * SPRING_MAIL_HOST). Fail fast: sem nenhum {@link EnviadorEmail}, a aplicação não sobe.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeEmail {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    EnviadorEmailSmtp enviadorEmailSmtp(JavaMailSender correio, @Value("${coe.email.remetente:}") String remetente) {
        return new EnviadorEmailSmtp(correio, remetente);
    }

    @Bean
    SmartInitializingSingleton provedorDeEmailObrigatorio(ObjectProvider<EnviadorEmail> enviadores) {
        return () -> {
            if (enviadores.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "Provedor de e-mail não configurado: defina spring.mail.host (SPRING_MAIL_HOST) e coe.email.remetente.");
            }
        };
    }
}
