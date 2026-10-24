package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chaves de cifra (COE_CIFRA_KID_ATUAL + COE_CIFRA_CHAVES): sem chave boa, a aplicação não sobe. */
class ChavesDeCifraTest {

    private static final String CHAVE_1 = CifraDeCampoTest.CHAVE_1;
    private static final String CHAVE_2 = CifraDeCampoTest.CHAVE_2;

    @Test
    @DisplayName("lê o kid atual e as chaves anteriores")
    void le() {
        ChavesDeCifra chaves = ChavesDeCifra.de("k2", "k2:" + CHAVE_2 + ", k1:" + CHAVE_1);

        assertThat(chaves.kidAtual()).isEqualTo("k2");
        assertThat(chaves.chave("k1")).isPresent();
        assertThat(chaves.chave("k3")).isEmpty();
    }

    @Test
    @DisplayName("configuração ruim não sobe, e a mensagem nunca traz a chave")
    void ruim() {
        assertThatThrownBy(() -> ChavesDeCifra.de("", "k1:" + CHAVE_1)).hasMessageContaining("COE_CIFRA_KID_ATUAL");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "")).hasMessageContaining("COE_CIFRA_CHAVES");
        assertThatThrownBy(() -> ChavesDeCifra.de("k2", "k1:" + CHAVE_1)).hasMessageContaining("kid atual");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "k1:nao-e-base64!")).hasMessageContaining("base64");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "k1:" + "c2VncmVkbw=="))
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining("c2VncmVkbw");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "k1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
                .hasMessageContaining("iguais");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "k1:" + CHAVE_1 + ",k1:" + CHAVE_2))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> ChavesDeCifra.de("k 1", "k 1:" + CHAVE_1)).hasMessageContaining("kid");
        assertThatThrownBy(() -> ChavesDeCifra.de("k1", "k1" + CHAVE_1)).hasMessageContaining("kid:base64");
    }

    @Test
    @DisplayName("toString não mostra chave")
    void toStringSemChave() {
        assertThat(ChavesDeCifra.de("k1", "k1:" + CHAVE_1).toString())
                .doesNotContain(CHAVE_1)
                .contains("k1");
    }
}
