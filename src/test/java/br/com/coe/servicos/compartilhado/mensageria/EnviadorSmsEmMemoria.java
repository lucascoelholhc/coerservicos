package br.com.coe.servicos.compartilhado.mensageria;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SMS falso dos testes: guarda as mensagens em memória para o teste ler o código. */
public class EnviadorSmsEmMemoria implements EnviadorSms {

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");

    public record Sms(String celular, String texto) {}

    private final ConcurrentLinkedDeque<Sms> enviados = new ConcurrentLinkedDeque<>();

    @Override
    public void enviar(String celular, String texto) {
        enviados.add(new Sms(celular, texto));
    }

    public List<Sms> para(String celular) {
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
