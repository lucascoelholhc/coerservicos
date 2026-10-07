package br.com.coe.servicos.compartilhado.mensageria;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envio assíncrono (decisão de 07/10): a mensagem só sai depois do commit (rollback não envia) e
 * numa fila própria e limitada, para a resposta não esperar o provedor nem o tempo revelar se o
 * destino tem conta. A submissão ao executor é explícita (equivale ao {@code @Async}) para que fila
 * cheia descarte com log mascarado e a contagem de pendentes fique exata. Erro do provedor: só log.
 */
@Component
public class DespachoDeMensagens implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(DespachoDeMensagens.class);
    private static final long OCIOSA_SEGUNDOS = 60;
    private static final long ESPERA_NO_DESLIGAMENTO_SEGUNDOS = 5;

    private final EnviadorSms sms;
    private final EnviadorEmail email;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger pendentes = new AtomicInteger();

    @Autowired
    public DespachoDeMensagens(
            EnviadorSms sms,
            EnviadorEmail email,
            @Value("${coe.mensageria.threads:2}") int threads,
            @Value("${coe.mensageria.threads-maximo:4}") int threadsMaximo,
            @Value("${coe.mensageria.fila:100}") int fila) {
        if (threads < 1 || threadsMaximo < threads || fila < 1) {
            throw new IllegalStateException(
                    "coe.mensageria inválida: threads >= 1, threads-maximo >= threads e fila >= 1" + " (veio " + threads
                            + ", " + threadsMaximo + ", " + fila + ")");
        }
        this.sms = sms;
        this.email = email;
        AtomicInteger numero = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads,
                threadsMaximo,
                OCIOSA_SEGUNDOS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(fila),
                tarefa -> {
                    Thread thread = new Thread(tarefa, "envio-" + numero.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Fora de transação (não deveria acontecer) envia direto, em vez de sumir em silêncio. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoConfirmar(MensagemPronta mensagem) {
        pendentes.incrementAndGet();
        try {
            executor.execute(() -> enviar(mensagem));
        } catch (RejectedExecutionException filaCheia) {
            pendentes.decrementAndGet();
            LOG.warn(
                    "Mensagem descartada (fila de envio cheia): {} para {}",
                    mensagem.canal(),
                    mensagem.destinoMascarado());
        }
    }

    private void enviar(MensagemPronta mensagem) {
        try {
            switch (mensagem.canal()) {
                case SMS -> sms.enviar(mensagem.destino(), mensagem.texto());
                case EMAIL -> email.enviar(mensagem.destino(), mensagem.assunto(), mensagem.texto());
            }
        } catch (RuntimeException erro) {
            LOG.error(
                    "Falha ao enviar {} para {}: {}",
                    mensagem.canal(),
                    mensagem.destinoMascarado(),
                    erro.getClass().getSimpleName());
        } finally {
            pendentes.decrementAndGet();
        }
    }

    /** Mensagens na fila ou sendo enviadas (testes e, no futuro, health). */
    public int pendentes() {
        return pendentes.get();
    }

    /** Desligando: termina o que está na fila (até 5 s) antes de parar as threads. */
    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(ESPERA_NO_DESLIGAMENTO_SEGUNDOS, TimeUnit.SECONDS)) {
                LOG.warn("Desligando com {} mensagem(ns) ainda na fila de envio", pendentes.get());
                executor.shutdownNow();
            }
        } catch (InterruptedException interrompido) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
