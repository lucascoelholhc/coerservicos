package br.com.coe.servicos.compartilhado.cripto;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chaves AES-256 da criptografia de campo: {@code COE_CIFRA_KID_ATUAL} (com qual cifrar) e
 * {@code COE_CIFRA_CHAVES} no formato {@code kid:base64,kid:base64} (a atual e as anteriores, para decifrar o
 * que já foi gravado). Rotação: nova chave com kid novo vira a atual; a anterior fica na lista até tudo ser
 * cifrado de novo. Configuração ruim impede a subida, e a mensagem nunca traz a chave. No prod as variáveis
 * vêm do Secrets Manager (AWS-04).
 */
public final class ChavesDeCifra {

    private static final int BYTES_DA_CHAVE = 32;
    private static final Pattern KID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final String kidAtual;
    private final Map<String, SecretKey> chaves;

    private ChavesDeCifra(String kidAtual, Map<String, SecretKey> chaves) {
        this.kidAtual = kidAtual;
        this.chaves = Map.copyOf(chaves);
    }

    public static ChavesDeCifra de(String kidAtual, String lista) {
        if (kidAtual == null || kidAtual.isBlank()) {
            throw invalida("falta o kid atual (COE_CIFRA_KID_ATUAL)");
        }
        String atual = kidAtual.strip();
        if (!KID.matcher(atual).matches()) {
            throw invalida("kid atual fora do padrão (letras, números, _ e -, até 32)");
        }
        if (lista == null || lista.isBlank()) {
            throw invalida("faltam as chaves (COE_CIFRA_CHAVES)");
        }
        Map<String, SecretKey> chaves = new LinkedHashMap<>();
        for (String item : lista.split(",")) {
            String[] partes = item.strip().split(":", 2);
            if (partes.length != 2) {
                throw invalida("cada chave no formato kid:base64");
            }
            String kid = partes[0].strip();
            if (!KID.matcher(kid).matches()) {
                throw invalida("kid fora do padrão (letras, números, _ e -, até 32)");
            }
            if (chaves.containsKey(kid)) {
                throw invalida("kid repetido: " + kid);
            }
            chaves.put(kid, new SecretKeySpec(bytesDaChave(kid, partes[1]), "AES"));
        }
        if (!chaves.containsKey(atual)) {
            throw invalida("o kid atual (" + atual + ") não está na lista de chaves");
        }
        return new ChavesDeCifra(atual, chaves);
    }

    private static byte[] bytesDaChave(String kid, String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException erro) {
            throw invalida("a chave " + kid + " não está em base64");
        }
        if (bytes.length != BYTES_DA_CHAVE) {
            throw invalida("a chave " + kid + " precisa ter 32 bytes (AES-256)");
        }
        if (todosIguais(bytes)) {
            throw invalida("a chave " + kid + " tem todos os bytes iguais (gere com um gerador aleatório)");
        }
        return bytes;
    }

    static boolean todosIguais(byte[] bytes) {
        for (byte valor : bytes) {
            if (valor != bytes[0]) {
                return false;
            }
        }
        return true;
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Chaves de cifra inválidas: " + motivo);
    }

    public String kidAtual() {
        return kidAtual;
    }

    public Optional<SecretKey> chave(String kid) {
        return Optional.ofNullable(chaves.get(kid));
    }

    /** Para conferir que a chave do HMAC é outra (uma chave nunca serve a dois usos). */
    boolean usa(byte[] chave) {
        return chaves.values().stream().anyMatch(existente -> Arrays.equals(existente.getEncoded(), chave));
    }

    @Override
    public String toString() {
        return "ChavesDeCifra[kidAtual=" + kidAtual + ", kids=" + chaves.keySet() + "]";
    }
}
