package br.com.coe.servicos.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Despacho assíncrono: fila limitada, descarte com log mascarado e erro do provedor só no log. */
@ExtendWith(OutputCaptureExtension.class)
class DespachoDeMensagensTest {

    private static final String CELULAR = "47900000101";
    private static final EnviadorEmail SEM_EMAIL = (para, assunto, texto) -> {
        throw new AssertionError("não devia mandar e-mail");
    };

    private final CountDownLatch liberar = new CountDownLatch(1);
    private final AtomicInteger enviados = new AtomicInteger();
    private DespachoDeMensagens despacho;

    @AfterEach
    void fechar() {
        liberar.countDown();
        if (despacho != null) {
            despacho.destroy();
        }
    }

    private EnviadorSms enviadorPreso() {
        return (celular, texto) -> {
            try {
                liberar.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
            enviados.incrementAndGet();
        };
    }

    @Test
    @DisplayName("fila cheia: descarta com log (celular mascarado), sem lançar nada para quem publicou")
    void filaCheiaDescarta(CapturedOutput saida) {
        despacho = new DespachoDeMensagens(enviadorPreso(), SEM_EMAIL, 1, 1, 1);

        assertThatCode(() -> {
                    for (int i = 0; i < 3; i++) {
                        despacho.aoConfirmar(MensagemPronta.sms(CELULAR, "COE: seu código é 123456."));
                    }
                })
                .doesNotThrowAnyException();

        assertThat(saida.getAll())
                .contains("fila de envio cheia")
                .contains("47*****0101")
                .doesNotContain(CELULAR)
                .doesNotContain("123456");
        liberar.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> despacho.pendentes() == 0);
        assertThat(enviados).hasValue(2);
    }

    @Test
    @DisplayName("erro do provedor: só log mascarado, e a contagem de pendentes volta a zero")
    void erroDoProvedor(CapturedOutput saida) {
        despacho = new DespachoDeMensagens(
                (celular, texto) -> {
                    throw new IllegalStateException("provedor fora " + celular);
                },
                SEM_EMAIL,
                1,
                1,
                10);

        despacho.aoConfirmar(MensagemPronta.sms(CELULAR, "COE: seu código é 654321."));

        await().atMost(Duration.ofSeconds(5)).until(() -> despacho.pendentes() == 0);
        assertThat(saida.getAll())
                .contains("Falha ao enviar SMS para 47*****0101")
                .doesNotContain(CELULAR)
                .doesNotContain("654321");
    }

    @Test
    @DisplayName("e-mail: vai pelo enviador de e-mail; erro do provedor só no log, com o endereço mascarado")
    void email(CapturedOutput saida) {
        AtomicInteger emails = new AtomicInteger();
        despacho = new DespachoDeMensagens(
                enviadorPreso(),
                (para, assunto, texto) -> {
                    if (emails.incrementAndGet() > 1) {
                        throw new IllegalStateException("smtp fora " + para);
                    }
                },
                1,
                1,
                10);

        despacho.aoConfirmar(MensagemPronta.email("ana.silva@exemplo.com", "Assunto", "link #token=abc"));
        despacho.aoConfirmar(MensagemPronta.email("ana.silva@exemplo.com", "Assunto", "link #token=def"));

        await().atMost(Duration.ofSeconds(5)).until(() -> despacho.pendentes() == 0);
        assertThat(emails).hasValue(2);
        assertThat(saida.getAll())
                .contains("Falha ao enviar EMAIL para a***@exemplo.com")
                .doesNotContain("ana.silva@exemplo.com")
                .doesNotContain("#token=def");
        assertThat(MensagemPronta.email("ana.silva@exemplo.com", "Assunto", "link #token=abc")
                        .toString())
                .doesNotContain("ana.silva")
                .doesNotContain("abc");
    }

    @Test
    @DisplayName("a mensagem não mostra destino nem texto no toString")
    void toStringMascarado() {
        assertThat(MensagemPronta.sms(CELULAR, "COE: seu código é 111222.").toString())
                .doesNotContain(CELULAR)
                .doesNotContain("111222");
    }

    @Test
    @DisplayName("configuração fora da faixa não sobe")
    void configuracaoInvalida() {
        assertThatCode(() -> new DespachoDeMensagens(enviadorPreso(), SEM_EMAIL, 0, 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.mensageria");
        assertThatCode(() -> new DespachoDeMensagens(enviadorPreso(), SEM_EMAIL, 2, 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new DespachoDeMensagens(enviadorPreso(), SEM_EMAIL, 1, 1, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
