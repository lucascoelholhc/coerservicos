package br.com.coe.servicos.compartilhado.erro;

/**
 * A operação conflita com o estado atual (HTTP 409), por exemplo dia já reservado, alteração
 * concorrente ou celular já cadastrado. O código vira o tipo do erro (urn:coe:erro:<codigo>) e o
 * campo, quando houver, sai no corpo para a tela destacar. A mensagem é mostrada ao usuário:
 * escreva em português simples.
 */
public class ConflitoException extends RuntimeException {

    private final String codigo;
    private final String campo;

    public ConflitoException(String mensagem) {
        this("conflito", mensagem, null);
    }

    public ConflitoException(String codigo, String mensagem, String campo) {
        super(mensagem);
        this.codigo = codigo;
        this.campo = campo;
    }

    public String getCodigo() {
        return codigo;
    }

    /** O campo do pedido que causou o conflito, ou nulo. */
    public String getCampo() {
        return campo;
    }
}
