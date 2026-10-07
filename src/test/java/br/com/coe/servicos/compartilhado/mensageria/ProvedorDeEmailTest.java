package br.com.coe.servicos.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

/** Sem SMTP configurado (spring.mail.host), a aplicação não sobe; com ele, o e-mail sai pelo SMTP. */
class ProvedorDeEmailTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDeEmail.class)
            .withPropertyValues("coe.email.remetente=nao-responda@coe.local");

    @Test
    @DisplayName("sem spring.mail.host: não sobe")
    void semSmtpNaoSobe() {
        contexto.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Provedor de e-mail");
        });
    }

    @Test
    @DisplayName("com spring.mail.host: o enviador por SMTP sobe")
    void comSmtpSobe() {
        contexto.withPropertyValues("spring.mail.host=localhost")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(EnviadorEmailSmtp.class));
    }

    @Test
    @DisplayName("com SMTP mas sem remetente: não sobe")
    void semRemetenteNaoSobe() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoDeEmail.class)
                .withPropertyValues("spring.mail.host=localhost")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasStackTraceContaining("coe.email.remetente");
                });
    }
}
