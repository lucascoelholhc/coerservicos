package br.com.coe.servicos.config;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chave do HMAC-SHA256 dos códigos SMS (COE_CHAVE_CODIGOS), separada da do JWT. No banco vai só o
 * HMAC: sem a chave, um vazamento da tabela não permite testar os 10^6 códigos. Configuração ruim
 * impede a subida e a mensagem nunca traz a chave.
 */
public final class ChaveDeCodigos {

    private static final int MINIMO_BYTES = 32;
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    private ChaveDeCodigos(SecretKeySpec chave) {
        this.chave = chave;
    }

    public static ChaveDeCodigos de(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw invalida("falta a chave");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException erro) {
            throw invalida("não está em base64");
        }
        if (bytes.length < MINIMO_BYTES) {
            throw invalida("tem menos de 32 bytes");
        }
        return new ChaveDeCodigos(new SecretKeySpec(bytes, ALGORITMO));
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Chave dos códigos inválida: " + motivo + " (COE_CHAVE_CODIGOS)");
    }

    /** HMAC-SHA256 (32 bytes) do texto. Um {@link Mac} por chamada: ele não é thread-safe. */
    public byte[] hmac(String texto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC-SHA256 indisponível na JVM", erro);
        }
    }

    @Override
    public String toString() {
        return "ChaveDeCodigos[***]";
    }
}
