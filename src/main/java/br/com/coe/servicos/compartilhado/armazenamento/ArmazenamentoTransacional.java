package br.com.coe.servicos.compartilhado.armazenamento;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * O {@link Armazenamento} que o resto do código recebe. {@code guardar} só roda dentro de uma transação:
 * a chave vai para o banco nela, e, se ela for desfeita, o arquivo vira órfão. Por isso o arquivo é apagado
 * depois do rollback, numa thread própria ({@code apagar-orfao}): nunca na thread da requisição nem dentro
 * da transação que acabou de terminar (lição do AFTER_COMMIT da CORE-07). Queda entre o PUT e o commit
 * ainda pode deixar órfão: a varredura do bucket fica para depois (plano, CORE-09).
 */
final class ArmazenamentoTransacional implements Armazenamento, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ArmazenamentoTransacional.class);
    private static final int FILA_DE_REMOCAO = 1000;
    private static final Duration ESPERA_AO_FECHAR = Duration.ofSeconds(5);

    private final Armazenamento destino;
    private final ThreadPoolExecutor apagador;

    ArmazenamentoTransacional(Armazenamento destino) {
        this.destino = destino;
        this.apagador = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(FILA_DE_REMOCAO),
                Thread.ofPlatform().name("apagar-orfao").daemon(true).factory());
    }

    @Override
    public String guardar(ArquivoValidado arquivo, Categoria categoria) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "guardar exige uma transação ativa (@Transactional): sem ela, o arquivo não é apagado se a"
                            + " gravação no banco falhar");
        }
        String chave = destino.guardar(arquivo, categoria);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    apagarDepois(chave);
                }
            }
        });
        return chave;
    }

    @Override
    public URI urlAssinada(String chave, Duration validade) {
        return destino.urlAssinada(chave, validade);
    }

    @Override
    public void apagar(String chave) {
        destino.apagar(chave);
    }

    private void apagarDepois(String chave) {
        try {
            apagador.execute(() -> {
                try {
                    destino.apagar(chave);
                } catch (RuntimeException erro) {
                    LOG.warn("Arquivo órfão não apagado (transação desfeita): {}", ArmazenamentoS3.paraLog(chave));
                }
            });
        } catch (RejectedExecutionException cheia) {
            LOG.warn("Arquivo órfão não apagado (fila cheia): {}", ArmazenamentoS3.paraLog(chave));
        }
    }

    @Override
    public void close() throws InterruptedException {
        apagador.shutdown();
        if (!apagador.awaitTermination(ESPERA_AO_FECHAR.toMillis(), TimeUnit.MILLISECONDS)) {
            LOG.warn(
                    "Remoções de arquivos órfãos interrompidas no desligamento: {}",
                    apagador.getQueue().size());
            apagador.shutdownNow();
        }
    }
}
