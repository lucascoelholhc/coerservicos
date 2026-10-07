package br.com.coe.servicos.compartilhado.mensageria;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMS falso dos testes: guarda as mensagens em memória. O envio é assíncrono, então toda leitura
 * espera (no máximo 5 s) a fila de envio esvaziar antes de olhar, sem sleep solto no teste.
 */
public class EnviadorSmsEmMemoria implements EnviadorSms {

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(5);

    public record Sms(String celular, String texto) {}

    private final ConcurrentLinkedDeque<Sms> enviados = new ConcurrentLinkedDeque<>();
    private final IntSupplier pendentes;
    private final AtomicInteger falhasProgramadas = new AtomicInteger();
    private volatile Duration atraso = Duration.ZERO;

    public EnviadorSmsEmMemoria(IntSupplier pendentes) {
        this.pendentes = pendentes;
    }

    @Override
    public void enviar(String celular, String texto) {
        if (falhasProgramadas.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("provedor de SMS fora do ar (teste)");
        }
        if (!atraso.isZero()) {
            try {
                Thread.sleep(atraso); // simula provedor lento (roda na thread de envio, não no teste)
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
        }
        enviados.add(new Sms(celular, texto));
    }

    /** Provedor lento a partir de agora (o teste confere que a requisição não espera). */
    public void atrasar(Duration atraso) {
        this.atraso = atraso;
    }

    /** O próximo envio falha no provedor. */
    public void falharProximo() {
        falhasProgramadas.incrementAndGet();
    }

    /** Volta ao normal (chamado antes de cada teste). */
    public void restaurar() {
        atraso = Duration.ZERO;
        falhasProgramadas.set(0);
    }

    /** Espera a fila de envio esvaziar (no máximo 5 s). */
    public void aguardarEnvios() {
        await().atMost(ESPERA_MAXIMA).pollInterval(Duration.ofMillis(10)).until(() -> pendentes.getAsInt() == 0);
    }

    public List<Sms> para(String celular) {
        aguardarEnvios();
        return enviados.stream().filter(sms -> sms.celular().equals(celular)).toList();
    }

    /** Código de 6 dígitos do último SMS para o celular. */
    public Optional<String> ultimoCodigo(String celular) {
        List<Sms> doCelular = para(celular);
        if (doCelular.isEmpty()) {
            return Optional.empty();
        }
        Matcher achou = CODIGO.matcher(doCelular.get(doCelular.size() - 1).texto());
        return achou.find() ? Optional.of(achou.group(1)) : Optional.empty();
    }
}
