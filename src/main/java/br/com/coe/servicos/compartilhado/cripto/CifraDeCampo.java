package br.com.coe.servicos.compartilhado.cripto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM por campo. Formato gravado: {@code [versão 1][tamanho do kid][kid][nonce 12][cifra + tag 16]}.
 * O nonce é aleatório a cada cifra (o mesmo valor nunca dá o mesmo resultado); o AAD é o cabeçalho + o
 * {@link Campo}, então mexer na versão, no kid ou trocar o dado de coluna é recusado. Cifra sempre com o kid
 * atual e decifra com qualquer kid conhecido (rotação).
 */
public final class CifraDeCampo {

    static final byte VERSAO = 1;
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG = TAG_BITS / 8;
    private static final int KID_MAXIMO = 32;
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";

    private final ChavesDeCifra chaves;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifraDeCampo(ChavesDeCifra chaves) {
        this.chaves = chaves;
    }

    /** Nulo continua nulo (coluna vazia no rascunho). */
    public byte[] cifrar(String texto, Campo campo) {
        if (texto == null) {
            return null;
        }
        String kid = chaves.kidAtual();
        byte[] cabecalho = cabecalho(kid);
        byte[] nonce = new byte[NONCE];
        aleatorio.nextBytes(nonce);
        try {
            Cipher cifra = Cipher.getInstance(TRANSFORMACAO);
            cifra.init(Cipher.ENCRYPT_MODE, chaves.chave(kid).orElseThrow(), new GCMParameterSpec(TAG_BITS, nonce));
            cifra.updateAAD(aad(cabecalho, campo));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(cabecalho.length + NONCE + cifrado.length)
                    .put(cabecalho)
                    .put(nonce)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("AES-GCM indisponível na JVM", erro);
        }
    }

    public String decifrar(byte[] dado, Campo campo) {
        if (dado == null) {
            return null;
        }
        if (dado.length < 2 || dado[0] != VERSAO) {
            throw new DadoCifradoInvalidoException(campo, "versão desconhecida");
        }
        int tamanhoDoKid = dado[1] & 0xFF;
        int inicioDoNonce = 2 + tamanhoDoKid;
        if (tamanhoDoKid == 0 || tamanhoDoKid > KID_MAXIMO || dado.length < inicioDoNonce + NONCE + TAG) {
            throw new DadoCifradoInvalidoException(campo, "formato inválido");
        }
        String kid = new String(dado, 2, tamanhoDoKid, StandardCharsets.US_ASCII);
        SecretKey chave = chaves.chave(kid)
                .orElseThrow(() -> new DadoCifradoInvalidoException(campo, "chave " + kid + " desconhecida"));
        try {
            Cipher cifra = Cipher.getInstance(TRANSFORMACAO);
            cifra.init(
                    Cipher.DECRYPT_MODE,
                    chave,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(dado, inicioDoNonce, inicioDoNonce + NONCE)));
            cifra.updateAAD(aad(Arrays.copyOfRange(dado, 0, inicioDoNonce), campo));
            byte[] texto = cifra.doFinal(dado, inicioDoNonce + NONCE, dado.length - inicioDoNonce - NONCE);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (AEADBadTagException erro) {
            throw new DadoCifradoInvalidoException(campo, "adulterado, de outro campo ou chave errada");
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("AES-GCM indisponível na JVM", erro);
        }
    }

    private static byte[] cabecalho(String kid) {
        byte[] kidBytes = kid.getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(2 + kidBytes.length)
                .put(VERSAO)
                .put((byte) kidBytes.length)
                .put(kidBytes)
                .array();
    }

    private static byte[] aad(byte[] cabecalho, Campo campo) {
        byte[] doCampo = campo.aad();
        return ByteBuffer.allocate(cabecalho.length + doCampo.length)
                .put(cabecalho)
                .put(doCampo)
                .array();
    }
}
