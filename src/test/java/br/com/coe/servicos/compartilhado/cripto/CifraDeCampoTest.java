package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AES-256-GCM por campo (CORE-10, RNF13): formato versionado com kid, nonce aleatório, AAD por campo. */
class CifraDeCampoTest {

    // Chaves SÓ de teste (não são segredo).
    static final String CHAVE_1 = "c2VncmVkby1kZS10ZXN0ZS0xLWNvbS0zMi1ieXRlcyE=";
    static final String CHAVE_2 = "b3V0cm8tc2VncmVkby1kZS10ZXN0ZS0yLTMyLWJ5dGU=";

    private final CifraDeCampo cifra = new CifraDeCampo(ChavesDeCifra.de("k1", "k1:" + CHAVE_1));

    @Test
    @DisplayName("ida e volta: decifra o que cifrou")
    void idaEVolta() {
        byte[] cifrado = cifra.cifrar("123.456.789-09", Campo.CPF);

        assertThat(cifra.decifrar(cifrado, Campo.CPF)).isEqualTo("123.456.789-09");
    }

    @Test
    @DisplayName("o texto em claro não aparece no resultado; o formato começa com a versão e o kid")
    void semTextoEmClaro() {
        byte[] cifrado = cifra.cifrar("12345678909", Campo.CPF);

        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain("12345678909");
        assertThat(cifrado[0]).isEqualTo((byte) 1);
        assertThat(cifrado[1]).isEqualTo((byte) 2);
        assertThat(new String(cifrado, 2, 2, StandardCharsets.US_ASCII)).isEqualTo("k1");
        // versão + tamanho do kid + kid + nonce de 12 + texto + tag de 16
        assertThat(cifrado).hasSize(1 + 1 + 2 + 12 + 11 + 16);
    }

    @Test
    @DisplayName("nonce diferente a cada cifra: o mesmo valor nunca dá o mesmo resultado")
    void nonceNovo() {
        byte[] primeiro = cifra.cifrar("12345678909", Campo.CPF);
        byte[] segundo = cifra.cifrar("12345678909", Campo.CPF);

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(Arrays.copyOfRange(primeiro, 4, 16)).isNotEqualTo(Arrays.copyOfRange(segundo, 4, 16));
    }

    @Test
    @DisplayName("qualquer byte adulterado (cabeçalho, nonce, cifra ou tag) é recusado, sem o valor na mensagem")
    void adulterado() {
        byte[] cifrado = cifra.cifrar("12345678909", Campo.CPF);
        for (int i = 0; i < cifrado.length; i++) {
            byte[] mexido = cifrado.clone();
            mexido[i] ^= 0x01;
            assertThatThrownBy(() -> cifra.decifrar(mexido, Campo.CPF))
                    .as("byte %d", i)
                    .isInstanceOf(DadoCifradoInvalidoException.class)
                    .hasMessageNotContaining("12345678909");
        }
    }

    @Test
    @DisplayName("dado de um campo não decifra como outro (AAD): CPF não vira endereço")
    void campoErrado() {
        byte[] cpf = cifra.cifrar("12345678909", Campo.CPF);

        assertThatThrownBy(() -> cifra.decifrar(cpf, Campo.ENDERECO)).isInstanceOf(DadoCifradoInvalidoException.class);
    }

    @Test
    @DisplayName("chave errada com o mesmo kid é recusada")
    void chaveErrada() {
        byte[] cifrado = cifra.cifrar("12345678909", Campo.CPF);
        CifraDeCampo outra = new CifraDeCampo(ChavesDeCifra.de("k1", "k1:" + CHAVE_2));

        assertThatThrownBy(() -> outra.decifrar(cifrado, Campo.CPF)).isInstanceOf(DadoCifradoInvalidoException.class);
    }

    @Test
    @DisplayName("rotação: decifra com qualquer kid conhecido e cifra sempre com o atual")
    void rotacao() {
        byte[] antigo = cifra.cifrar("Rua A, 10", Campo.ENDERECO);
        CifraDeCampo rotacionada = new CifraDeCampo(ChavesDeCifra.de("k2", "k2:" + CHAVE_2 + ",k1:" + CHAVE_1));

        byte[] novo = rotacionada.cifrar("Rua A, 10", Campo.ENDERECO);

        assertThat(rotacionada.decifrar(antigo, Campo.ENDERECO)).isEqualTo("Rua A, 10");
        assertThat(new String(novo, 2, 2, StandardCharsets.US_ASCII)).isEqualTo("k2");
        assertThat(rotacionada.decifrar(novo, Campo.ENDERECO)).isEqualTo("Rua A, 10");
    }

    @Test
    @DisplayName("kid desconhecido (chave antiga já removida) é recusado")
    void kidDesconhecido() {
        byte[] antigo = cifra.cifrar("12345678909", Campo.CPF);
        CifraDeCampo semAAntiga = new CifraDeCampo(ChavesDeCifra.de("k2", "k2:" + CHAVE_2));

        assertThatThrownBy(() -> semAAntiga.decifrar(antigo, Campo.CPF))
                .isInstanceOf(DadoCifradoInvalidoException.class)
                .hasMessageContaining("k1");
    }

    @Test
    @DisplayName("lixo, vazio e versão desconhecida são recusados; nulo continua nulo")
    void entradasRuins() {
        assertThatThrownBy(() -> cifra.decifrar(new byte[3], Campo.CPF))
                .isInstanceOf(DadoCifradoInvalidoException.class);
        assertThatThrownBy(() -> cifra.decifrar(new byte[0], Campo.CPF))
                .isInstanceOf(DadoCifradoInvalidoException.class);
        byte[] versao2 = cifra.cifrar("x", Campo.CPF);
        versao2[0] = 2;
        assertThatThrownBy(() -> cifra.decifrar(versao2, Campo.CPF)).isInstanceOf(DadoCifradoInvalidoException.class);
        byte[] kidLongo = cifra.cifrar("x", Campo.CPF);
        kidLongo[1] = (byte) 120;
        assertThatThrownBy(() -> cifra.decifrar(kidLongo, Campo.CPF)).isInstanceOf(DadoCifradoInvalidoException.class);
        assertThat(cifra.cifrar(null, Campo.CPF)).isNull();
        assertThat(cifra.decifrar(null, Campo.CPF)).isNull();
    }

    @Test
    @DisplayName("acentos e textos longos (endereço) voltam iguais")
    void utf8() {
        String endereco = "Rua São João, 1.234, apto 56 — bloco C, Itajaí/SC";

        assertThat(cifra.decifrar(cifra.cifrar(endereco, Campo.ENDERECO), Campo.ENDERECO))
                .isEqualTo(endereco);
    }
}
