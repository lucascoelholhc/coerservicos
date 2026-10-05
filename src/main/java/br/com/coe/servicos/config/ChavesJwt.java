package br.com.coe.servicos.config;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chaves HMAC do JWT por kid. A atual assina; todas (atual + anterior) validam. Configuração ruim
 * impede a subida (fail fast) e a mensagem nunca traz o conteúdo da chave.
 */
public final class ChavesJwt {

    private static final int MINIMO_BYTES = 32; // HS256 exige chave de pelo menos 256 bits
    private static final String ALGORITMO = "HmacSHA256";

    private final String kidAtual;
    private final Map<String, SecretKey> chaves;

    private ChavesJwt(String kidAtual, Map<String, SecretKey> chaves) {
        this.kidAtual = kidAtual;
        this.chaves = Map.copyOf(chaves);
    }

    /** Lê {@code kid:base64,kid:base64}; o kid atual precisa estar na lista. */
    public static ChavesJwt de(String kidAtual, String texto) {
        if (kidAtual == null || kidAtual.isBlank()) {
            throw invalida("falta o kid atual (COE_JWT_KID_ATUAL)");
        }
        if (texto == null || texto.isBlank()) {
            throw invalida("faltam as chaves (COE_JWT_CHAVES)");
        }
        Map<String, SecretKey> chaves = new LinkedHashMap<>();
        for (String par : texto.split(",")) {
            int separador = par.indexOf(':');
            if (separador <= 0) {
                throw invalida("formato esperado kid:base64, separados por vírgula");
            }
            String kid = par.substring(0, separador).strip();
            byte[] bytes = decodificar(kid, par.substring(separador + 1).strip());
            if (bytes.length < MINIMO_BYTES) {
                throw invalida("a chave do kid " + kid + " tem menos de 256 bits");
            }
            if (chaves.put(kid, new SecretKeySpec(bytes, ALGORITMO)) != null) {
                throw invalida("kid repetido: " + kid);
            }
        }
        String atual = kidAtual.strip();
        if (!chaves.containsKey(atual)) {
            throw invalida("o kid atual " + atual + " não está entre as chaves");
        }
        return new ChavesJwt(atual, chaves);
    }

    private static byte[] decodificar(String kid, String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException erro) {
            throw invalida("a chave do kid " + kid + " não está em base64");
        }
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Chaves do JWT inválidas: " + motivo);
    }

    public String kidAtual() {
        return kidAtual;
    }

    public SecretKey chaveAtual() {
        return chaves.get(kidAtual);
    }

    public Map<String, SecretKey> todas() {
        return chaves;
    }

    @Override
    public String toString() {
        return "ChavesJwt[kidAtual=" + kidAtual + ", kids=" + chaves.keySet() + "]";
    }
}
