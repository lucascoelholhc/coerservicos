package br.com.coe.servicos.compartilhado.erro;

/**
 * A operação conflita com o estado atual (HTTP 409), por exemplo dia já reservado
 * ou alteração concorrente. A mensagem é mostrada ao usuário: escreva em português simples.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
