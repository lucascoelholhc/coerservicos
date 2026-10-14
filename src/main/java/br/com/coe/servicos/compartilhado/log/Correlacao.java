package br.com.coe.servicos.compartilhado.log;

import org.slf4j.MDC;

/**
 * Id de correlação por requisição (CORE-11): gerado pelo FiltroDeCorrelacao, fica no MDC com a chave
 * {@link #CHAVE}, volta no header {@link #HEADER} e no Problem Details (campo {@code requisicao}) e segue
 * para a thread da fila de envio. O id que vier de fora nunca é aceito.
 */
public final class Correlacao {

    public static final String CHAVE = "requisicao";
    public static final String HEADER = "X-Request-Id";

    private Correlacao() {}

    /** O id da requisição desta thread, ou nulo fora de uma requisição. */
    public static String atual() {
        return MDC.get(CHAVE);
    }
}
