package br.com.coe.servicos.compartilhado.erro;

/**
 * Quem pede é conhecido, mas a ação não é permitida agora (HTTP 403), por exemplo conta suspensa
 * ou segundo passo do login pendente. O código vira o tipo do erro.
 */
public class AcaoProibidaException extends RuntimeException {

    private final String codigo;

    public AcaoProibidaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
