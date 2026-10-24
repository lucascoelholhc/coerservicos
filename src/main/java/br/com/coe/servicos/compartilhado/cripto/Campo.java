package br.com.coe.servicos.compartilhado.cripto;

import java.nio.charset.StandardCharsets;

/**
 * Qual dado está cifrado. Entra no AAD do GCM: o CPF cifrado não decifra como endereço nem como chave Pix,
 * então trocar o dado de coluna é recusado. Trocar de linha só é recusado com contexto (ver
 * {@link CifraDeCampo#cifrar(String, Campo, String)}). O nome nunca muda (dado gravado depende dele).
 */
public enum Campo {
    CPF("cpf"),
    CHAVE_PIX("chave-pix"),
    ENDERECO("endereco");

    private final byte[] aad;
    private final String nome;

    Campo(String nome) {
        this.nome = nome;
        this.aad = ("coe:" + nome).getBytes(StandardCharsets.US_ASCII);
    }

    byte[] aad() {
        return aad.clone();
    }

    String nome() {
        return nome;
    }
}
