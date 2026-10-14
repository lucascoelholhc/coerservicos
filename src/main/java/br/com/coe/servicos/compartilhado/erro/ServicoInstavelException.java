package br.com.coe.servicos.compartilhado.erro;

/**
 * Dependência essencial fora do ar (ex.: o banco do limitador de taxa): 503. A requisição é recusada
 * em vez de seguir sem a proteção (fail-closed).
 */
public class ServicoInstavelException extends RuntimeException {

    public ServicoInstavelException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
