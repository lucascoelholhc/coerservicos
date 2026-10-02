package br.com.coe.servicos.compartilhado.erro;

import java.util.regex.Pattern;

/**
 * Uma regra de negócio impediu a operação (HTTP 422). O código identifica a regra
 * (ex.: {@code limite-lc150}) e vira o tipo do erro: {@code urn:coe:erro:limite-lc150}.
 * A mensagem é mostrada ao usuário: escreva em português simples.
 */
public class RegraDeNegocioException extends RuntimeException {

    /** kebab-case: minúsculas e dígitos separados por um hífen (ex.: limite-lc150). */
    private static final Pattern CODIGO_VALIDO = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final String codigo;

    public RegraDeNegocioException(String codigo, String mensagem) {
        super(mensagem);
        if (codigo == null || !CODIGO_VALIDO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("Código de regra fora do padrão kebab-case: " + codigo);
        }
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
