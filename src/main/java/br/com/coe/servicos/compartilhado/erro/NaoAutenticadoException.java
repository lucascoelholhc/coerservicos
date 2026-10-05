package br.com.coe.servicos.compartilhado.erro;

import java.util.Map;

/**
 * Credencial ou sessão recusada (HTTP 401, com {@code WWW-Authenticate: Bearer}). O código vira o
 * tipo do erro; cabeçalhos extras (ex.: Set-Cookie que apaga o refresh) vão junto na resposta.
 */
public class NaoAutenticadoException extends RuntimeException {

    private final String codigo;
    private final Map<String, String> cabecalhos;

    public NaoAutenticadoException(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }

    public NaoAutenticadoException(String codigo, String mensagem, Map<String, String> cabecalhos) {
        super(mensagem);
        this.codigo = codigo;
        this.cabecalhos = Map.copyOf(cabecalhos);
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, String> getCabecalhos() {
        return cabecalhos;
    }
}
