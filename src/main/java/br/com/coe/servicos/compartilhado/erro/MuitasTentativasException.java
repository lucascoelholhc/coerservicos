package br.com.coe.servicos.compartilhado.erro;

/** Limite de pedidos atingido (HTTP 429), por exemplo SMS pedido antes de 60 s. */
public class MuitasTentativasException extends RuntimeException {

    public MuitasTentativasException(String mensagem) {
        super(mensagem);
    }
}
