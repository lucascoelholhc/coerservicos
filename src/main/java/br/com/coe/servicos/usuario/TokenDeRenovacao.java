package br.com.coe.servicos.usuario;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Refresh token: 32 bytes aleatórios em base64url. No banco vai só o SHA-256; o token em claro
 * existe só no cookie do aparelho.
 */
final class TokenDeRenovacao {

    private static final int BYTES = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenDeRenovacao() {}

    static String gerar() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash do token recebido; vazio se não tiver o formato de um token nosso. */
    static Optional<byte[]> hash(String token) {
        if (token == null) {
            return Optional.empty();
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException erro) {
            return Optional.empty();
        }
        return bytes.length == BYTES ? Optional.of(sha256(bytes)) : Optional.empty();
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", erro);
        }
    }
}
