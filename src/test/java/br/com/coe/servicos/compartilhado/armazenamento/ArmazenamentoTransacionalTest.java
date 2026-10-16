package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;

/**
 * Quem guarda um arquivo grava a chave no banco na mesma transação. Desfez a transação, o arquivo fica
 * órfão no bucket: o {@link Armazenamento} injetado apaga sozinho, numa thread própria (nunca na da
 * requisição nem dentro da transação que acabou, lição do AFTER_COMMIT da CORE-07).
 */
@ExtendWith(OutputCaptureExtension.class)
class ArmazenamentoTransacionalTest extends IntegracaoTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private PlatformTransactionManager transacoes;

    @Test
    @DisplayName("o Armazenamento injetado é o transacional")
    void injetadoEhOTransacional() {
        assertThat(armazenamento).isInstanceOf(ArmazenamentoTransacional.class);
    }

    @Test
    @DisplayName("guardar fora de uma transação falha alto (o rollback não teria como apagar)")
    void exigeTransacao() {
        assertThatThrownBy(() -> armazenamento.guardar(arquivo(), Categoria.PEDIDO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transação");
    }

    @Test
    @DisplayName("transação confirmada: o arquivo fica")
    void commitMantem() throws Exception {
        String chave = new TransactionTemplate(transacoes)
                .execute(status -> armazenamento.guardar(arquivo(), Categoria.PORTFOLIO));

        assertThat(baixar(armazenamento.urlAssinada(chave, Duration.ofMinutes(1))))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("transação desfeita: o arquivo é apagado numa thread própria, fora da requisição")
    void rollbackApaga(CapturedOutput saida) {
        AtomicReference<String> chave = new AtomicReference<>();

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            chave.set(armazenamento.guardar(arquivo(), Categoria.DOCUMENTO));
            status.setRollbackOnly();
        });

        await().atMost(Duration.ofSeconds(10))
                .until(() -> baixar(armazenamento.urlAssinada(chave.get(), Duration.ofMinutes(1))) == 404);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> saida.getOut()
                        .lines()
                        .anyMatch(linha -> linha.contains("Arquivo apagado: " + ArmazenamentoS3.paraLog(chave.get()))
                                && linha.contains("apagar-orfao")));
    }

    @Test
    @DisplayName("exceção dentro da transação: o arquivo também é apagado")
    void excecaoApaga() {
        AtomicReference<String> chave = new AtomicReference<>();

        assertThatThrownBy(() -> new TransactionTemplate(transacoes).executeWithoutResult(status -> {
                    chave.set(armazenamento.guardar(arquivo(), Categoria.EVIDENCIA));
                    throw new IllegalStateException("falhou depois de guardar");
                }))
                .hasMessage("falhou depois de guardar");

        await().atMost(Duration.ofSeconds(10))
                .until(() -> baixar(armazenamento.urlAssinada(chave.get(), Duration.ofMinutes(1))) == 404);
    }

    private static ArquivoValidado arquivo() {
        return new ArquivoValidado(ImagensDeTeste.png(8, 8), TipoDeImagem.PNG);
    }

    private static int baixar(URI url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
