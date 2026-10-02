package br.com.coe.servicos.compartilhado.erro;

/**
 * O recurso pedido não existe ou não pertence a quem pediu (HTTP 404).
 * Use também para recurso de outro usuário: não revela que ele existe (anti-IDOR).
 * A mensagem é mostrada ao usuário: escreva em português simples.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
