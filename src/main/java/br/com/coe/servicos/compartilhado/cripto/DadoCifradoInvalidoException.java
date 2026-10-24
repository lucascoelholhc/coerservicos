package br.com.coe.servicos.compartilhado.cripto;

/**
 * Dado cifrado que não decifra: adulterado, de outro campo, com chave errada ou com kid que não existe mais.
 * A mensagem diz o campo e o motivo, nunca o valor. Vira 500 (é erro de dado ou de configuração, não do usuário).
 */
public class DadoCifradoInvalidoException extends RuntimeException {

    DadoCifradoInvalidoException(Campo campo, String motivo) {
        super("Dado cifrado inválido (campo " + campo.nome() + "): " + motivo);
    }
}
