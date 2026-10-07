package br.com.coe.servicos.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Sem provedor de SMS a aplicação não sobe; o que escreve no log só existe no perfil local. */
@ExtendWith(OutputCaptureExtension.class)
class ProvedorDeSmsTest {

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(ConfiguracaoDeSms.class, EnviadorSmsNoLog.class);

    @Test
    @DisplayName("prod (ou qualquer perfil sem provedor): não sobe")
    void prodSemProvedorNaoSobe() {
        contexto.withPropertyValues("spring.profiles.active=prod").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Provedor de SMS");
        });
    }

    @Test
    @DisplayName("local: o fake que escreve no log sobe")
    void localSobeComOFakeDeLog() {
        contexto.withPropertyValues("spring.profiles.active=local")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(EnviadorSmsNoLog.class));
    }

    @Test
    @DisplayName("local junto com prod: não sobe (o SMS falso escreve o código no log)")
    void localComProdNaoSobe() {
        contexto.withPropertyValues("spring.profiles.active=local,prod").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("perfil prod");
        });
    }

    @Test
    @DisplayName("com um provedor de verdade, sobe em qualquer perfil")
    void comProvedorSobe() {
        contexto.withPropertyValues("spring.profiles.active=prod")
                .withBean(EnviadorSms.class, () -> (celular, texto) -> {})
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("o fake de log mascara o celular (o código aparece: é o jeito de entrar no local)")
    void fakeDeLogMascaraOCelular(CapturedOutput saida) {
        new EnviadorSmsNoLog().enviar("47900000101", "COE: seu código é 123456.");

        assertThat(saida.getAll()).contains("47*****0101").contains("123456").doesNotContain("47900000101");
    }
}
