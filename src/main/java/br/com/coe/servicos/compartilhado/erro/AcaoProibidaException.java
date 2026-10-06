package br.com.coe.servicos.compartilhado.erro;

import java.util.Map;

/**
 * Quem pede é conhecido, mas a ação não é permitida agora (HTTP 403), por exemplo conta suspensa
 * ou segundo passo do login pendente. O código vira o tipo do erro.
 */
public class AcaoProibidaException extends RuntimeException {

    private final String codigo;
    private final Map<String, Object> propriedades;

    public AcaoProibidaException(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }

    /** Com campos extras no corpo do erro (ex.: o {@code desafioId} do segundo passo do login). */
    public AcaoProibidaException(String codigo, String mensagem, Map<String, Object> propriedades) {
        super(mensagem);
        this.codigo = codigo;
        this.propriedades = Map.copyOf(propriedades);
    }

    public Map<String, Object> getPropriedades() {
        return propriedades;
    }

    public String getCodigo() {
        return codigo;
    }
}
