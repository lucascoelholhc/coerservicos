package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A thread de remoção sem S3 nem banco: falha ao apagar não propaga, não trava a fila, e o fechamento termina. */
class ArmazenamentoTransacionalUnidadeTest {

    private final DestinoFalso destino = new DestinoFalso();
    private final ArmazenamentoTransacional armazenamento = new ArmazenamentoTransacional(destino);

    @AfterEach
    void limpar() throws InterruptedException {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        armazenamento.close();
    }

    @Test
    @DisplayName("falha ao apagar o órfão só gera aviso; a próxima remoção ainda acontece")
    void falhaNaoTravaAFila() {
        destino.falharNaPrimeira.set(1);

        desfazer(() -> armazenamento.guardar(arquivo(), Categoria.PEDIDO));
        desfazer(() -> armazenamento.guardar(arquivo(), Categoria.PEDIDO));

        await().atMost(Duration.ofSeconds(5)).until(() -> destino.apagadas.size() == 1);
        assertThat(destino.tentativas.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("transação confirmada: nada é apagado")
    void commitNaoApaga() throws InterruptedException {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        armazenamento.guardar(arquivo(), Categoria.PEDIDO);
        terminar(TransactionSynchronization.STATUS_COMMITTED);

        armazenamento.close();

        assertThat(destino.tentativas.get()).isZero();
    }

    @Test
    @DisplayName("fechar espera as remoções em andamento e termina")
    void fechar() {
        desfazer(() -> armazenamento.guardar(arquivo(), Categoria.PEDIDO));

        assertThatCode(armazenamento::close).doesNotThrowAnyException();
        assertThat(destino.apagadas).hasSize(1);
    }

    private void desfazer(Runnable dentroDaTransacao) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        dentroDaTransacao.run();
        terminar(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private static void terminar(int status) {
        List<TransactionSynchronization> sincronizacoes = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        sincronizacoes.forEach(sincronizacao -> sincronizacao.afterCompletion(status));
    }

    private static ArquivoValidado arquivo() {
        return new ArquivoValidado(new byte[] {1, 2, 3}, TipoDeImagem.PNG);
    }

    private static final class DestinoFalso implements Armazenamento {

        private final AtomicInteger falharNaPrimeira = new AtomicInteger();
        private final AtomicInteger tentativas = new AtomicInteger();
        private final List<String> apagadas = new CopyOnWriteArrayList<>();

        @Override
        public String guardar(ArquivoValidado arquivo, Categoria categoria) {
            return ArmazenamentoS3.novaChave(categoria, arquivo.tipo());
        }

        @Override
        public URI urlAssinada(String chave, Duration validade) {
            return URI.create("http://localhost/" + chave);
        }

        @Override
        public void apagar(String chave) {
            tentativas.incrementAndGet();
            if (falharNaPrimeira.getAndDecrement() > 0) {
                throw new IllegalStateException("S3 fora do ar");
            }
            apagadas.add(chave);
        }
    }
}
