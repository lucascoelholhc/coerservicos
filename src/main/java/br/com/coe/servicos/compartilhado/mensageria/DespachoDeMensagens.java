package br.com.coe.servicos.compartilhado.mensageria;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.coe.servicos.compartilhado.log.Correlacao;

/**
 * Envio assíncrono (decisão de 07/10): a mensagem só sai depois do commit (rollback não envia) e
 * numa fila própria e limitada, para a resposta não esperar o provedor nem o tempo revelar se o
 * destino tem conta. Duas filas (CORE-07): a prioritária (login por código, MFA, recuperação de senha)
 * não espera a normal (prova de posse, confirmações), então uma inundação de posse não segura o código
 * de login. Fila cheia descarta com log mascarado e devolve a cota da linha que gerou a mensagem.
 * Erro do provedor: só log.
 */
@Component
public class DespachoDeMensagens implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(DespachoDeMensagens.class);
    private static final long OCIOSA_SEGUNDOS = 60;
    private static final long ESPERA_NO_DESLIGAMENTO_SEGUNDOS = 5;

    /** Tamanho de uma fila: threads fixas, threads no pico (só quando a fila enche) e capacidade. */
    public record Fila(int threads, int threadsMaximo, int capacidade) {

        public Fila {
            if (threads < 1 || threadsMaximo < threads || capacidade < 1) {
                throw new IllegalStateException(
                        "coe.mensageria inválida: threads >= 1, threads-maximo >= threads e fila >= 1" + " (veio "
                                + threads + ", " + threadsMaximo + ", " + capacidade + ")");
            }
        }
    }

    private final EnviadorSms sms;
    private final EnviadorEmail email;
    private final DevolucaoDeCota devolucao;
    private final ThreadPoolExecutor prioritaria;
    private final ThreadPoolExecutor normal;
    private final AtomicInteger pendentes = new AtomicInteger();

    @Autowired
    DespachoDeMensagens(
            EnviadorSms sms,
            EnviadorEmail email,
            DevolucaoDeCota devolucao,
            @Value("${coe.mensageria.prioritaria.threads:2}") int threadsPrioritaria,
            @Value("${coe.mensageria.prioritaria.threads-maximo:4}") int maximoPrioritaria,
            @Value("${coe.mensageria.prioritaria.fila:100}") int filaPrioritaria,
            @Value("${coe.mensageria.normal.threads:1}") int threadsNormal,
            @Value("${coe.mensageria.normal.threads-maximo:2}") int maximoNormal,
            @Value("${coe.mensageria.normal.fila:50}") int filaNormal) {
        this(
                sms,
                email,
                devolucao,
                new Fila(threadsPrioritaria, maximoPrioritaria, filaPrioritaria),
                new Fila(threadsNormal, maximoNormal, filaNormal));
    }

    public DespachoDeMensagens(
            EnviadorSms sms, EnviadorEmail email, DevolucaoDeCota devolucao, Fila prioritaria, Fila normal) {
        this.sms = sms;
        this.email = email;
        this.devolucao = devolucao;
        this.prioritaria = executor("envio-prioritario-", prioritaria);
        this.normal = executor("envio-", normal);
    }

    private static ThreadPoolExecutor executor(String prefixo, Fila fila) {
        AtomicInteger numero = new AtomicInteger();
        return new ThreadPoolExecutor(
                fila.threads(),
                fila.threadsMaximo(),
                OCIOSA_SEGUNDOS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(fila.capacidade()),
                tarefa -> {
                    Thread thread = new Thread(tarefa, prefixo + numero.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Fora de transação (não deveria acontecer) envia direto, em vez de sumir em silêncio. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoConfirmar(MensagemPronta mensagem) {
        ThreadPoolExecutor fila = mensagem.prioridade() == MensagemPronta.Prioridade.PRIORITARIA ? prioritaria : normal;
        pendentes.incrementAndGet();
        try {
            fila.execute(() -> enviar(mensagem));
        } catch (RejectedExecutionException filaCheia) {
            pendentes.decrementAndGet();
            LOG.warn(
                    "Mensagem descartada (fila de envio cheia, {}): {} para {}",
                    mensagem.prioridade(),
                    mensagem.canal(),
                    mensagem.destinoMascarado());
            devolverCota(mensagem);
        }
    }

    private void devolverCota(MensagemPronta mensagem) {
        if (mensagem.cota() == null) {
            return;
        }
        try {
            devolucao.devolver(mensagem.cota());
        } catch (RuntimeException erro) {
            LOG.error(
                    "Cota não devolvida ({} para {}): {}",
                    mensagem.canal(),
                    mensagem.destinoMascarado(),
                    erro.getClass().getSimpleName());
        }
    }

    private void enviar(MensagemPronta mensagem) {
        if (mensagem.requisicao() != null) {
            MDC.put(Correlacao.CHAVE, mensagem.requisicao());
        }
        try {
            switch (mensagem.canal()) {
                case SMS -> sms.enviar(mensagem.destino(), mensagem.texto());
                case EMAIL -> email.enviar(mensagem.destino(), mensagem.assunto(), mensagem.texto());
            }
            LOG.info("Enviado {} para {}", mensagem.canal(), mensagem.destinoMascarado());
        } catch (RuntimeException erro) {
            LOG.error(
                    "Falha ao enviar {} para {}: {}",
                    mensagem.canal(),
                    mensagem.destinoMascarado(),
                    erro.getClass().getSimpleName());
        } finally {
            pendentes.decrementAndGet();
            MDC.remove(Correlacao.CHAVE);
        }
    }

    /** Mensagens na fila ou sendo enviadas, nas duas filas (testes e, no futuro, health). */
    public int pendentes() {
        return pendentes.get();
    }

    /** Desligando: termina o que está nas filas (até 5 s) antes de parar as threads. */
    @Override
    public void destroy() {
        prioritaria.shutdown();
        normal.shutdown();
        try {
            boolean terminou = prioritaria.awaitTermination(ESPERA_NO_DESLIGAMENTO_SEGUNDOS, TimeUnit.SECONDS)
                    & normal.awaitTermination(ESPERA_NO_DESLIGAMENTO_SEGUNDOS, TimeUnit.SECONDS);
            if (!terminou) {
                LOG.warn("Desligando com {} mensagem(ns) ainda na fila de envio", pendentes.get());
                prioritaria.shutdownNow();
                normal.shutdownNow();
            }
        } catch (InterruptedException interrompido) {
            prioritaria.shutdownNow();
            normal.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
