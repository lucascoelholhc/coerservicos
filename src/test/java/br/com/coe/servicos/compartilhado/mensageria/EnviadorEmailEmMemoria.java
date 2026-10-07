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

/** E-mail falso dos testes; como o SMS, toda leitura espera a fila de envio esvaziar (até 5 s). */
public class EnviadorEmailEmMemoria implements EnviadorEmail {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");
    private static final Pattern LINK = Pattern.compile("(https?://\\S+)");
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(5);

    public record Email(String para, String assunto, String texto) {}

    private final ConcurrentLinkedDeque<Email> enviados = new ConcurrentLinkedDeque<>();
    private final IntSupplier pendentes;
    private final AtomicInteger falhasProgramadas = new AtomicInteger();
    private volatile Duration atraso = Duration.ZERO;

    public EnviadorEmailEmMemoria(IntSupplier pendentes) {
        this.pendentes = pendentes;
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        if (falhasProgramadas.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("provedor de e-mail fora do ar (teste)");
        }
        if (!atraso.isZero()) {
            try {
                Thread.sleep(atraso); // provedor lento (roda na thread de envio, não no teste)
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
        }
        enviados.add(new Email(para, assunto, texto));
    }

    public void atrasar(Duration atraso) {
        this.atraso = atraso;
    }

    public void falharProximo() {
        falhasProgramadas.incrementAndGet();
    }

    public void restaurar() {
        atraso = Duration.ZERO;
        falhasProgramadas.set(0);
    }

    public void aguardarEnvios() {
        await().atMost(ESPERA_MAXIMA).pollInterval(Duration.ofMillis(10)).until(() -> pendentes.getAsInt() == 0);
    }

    public List<Email> para(String endereco) {
        aguardarEnvios();
        return enviados.stream().filter(email -> email.para().equals(endereco)).toList();
    }

    private Optional<Email> ultimo(String endereco) {
        List<Email> doEndereco = para(endereco);
        return doEndereco.isEmpty() ? Optional.empty() : Optional.of(doEndereco.get(doEndereco.size() - 1));
    }

    /** Link do último e-mail para o endereço. */
    public Optional<String> ultimoLink(String endereco) {
        return ultimo(endereco)
                .map(Email::texto)
                .map(LINK::matcher)
                .filter(Matcher::find)
                .map(achou -> achou.group(1));
    }

    /** Token (do fragmento #token=...) do último e-mail para o endereço. */
    public Optional<String> ultimoToken(String endereco) {
        return ultimo(endereco)
                .map(Email::texto)
                .map(TOKEN::matcher)
                .filter(Matcher::find)
                .map(achou -> achou.group(1));
    }
}
