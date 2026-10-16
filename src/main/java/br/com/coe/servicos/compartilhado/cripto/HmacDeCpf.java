package br.com.coe.servicos.compartilhado.cripto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/**
 * HMAC-SHA256 do CPF ({@code COE_CHAVE_HMAC_CPF}, separada das chaves de cifra): vai na coluna
 * {@code cpf_hash} (única) para unicidade e busca exata, sem decifrar nada. Sem a chave, um vazamento da
 * tabela não permite testar os CPFs possíveis. Trocar esta chave exige recalcular todos os hashes.
 */
public final class HmacDeCpf {

    private static final int MINIMO_BYTES = 32;
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    private HmacDeCpf(SecretKeySpec chave) {
        this.chave = chave;
    }

    public static HmacDeCpf de(String base64) {
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
        if (ChavesDeCifra.todosIguais(bytes)) {
            throw invalida("tem todos os bytes iguais (gere com um gerador aleatório)");
        }
        return new HmacDeCpf(new SecretKeySpec(bytes, ALGORITMO));
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Chave do HMAC do CPF inválida: " + motivo + " (COE_CHAVE_HMAC_CPF)");
    }

    /** 32 bytes. Um {@link Mac} por chamada: ele não é thread-safe. */
    public byte[] de(Cpf cpf) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return mac.doFinal(cpf.digitos().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC-SHA256 indisponível na JVM", erro);
        }
    }

    byte[] bytesDaChave() {
        return chave.getEncoded();
    }

    @Override
    public String toString() {
        return "HmacDeCpf[***]";
    }
}
