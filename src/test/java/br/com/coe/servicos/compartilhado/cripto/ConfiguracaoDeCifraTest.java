package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Uma chave nunca serve a dois usos: a do HMAC do CPF não pode ser uma das chaves de cifra. */
class ConfiguracaoDeCifraTest {

    private final ConfiguracaoDeCifra configuracao = new ConfiguracaoDeCifra();
    private final ChavesDeCifra chaves = ChavesDeCifra.de("k1", "k1:" + CifraDeCampoTest.CHAVE_1);

    @Test
    @DisplayName("chave do HMAC igual a uma chave de cifra: não sobe")
    void hmacIgualACifra() {
        assertThatThrownBy(() -> configuracao.hmacDeCpf(CifraDeCampoTest.CHAVE_1, chaves))
                .hasMessageContaining("igual a uma chave de cifra")
                .hasMessageNotContaining(CifraDeCampoTest.CHAVE_1);
    }

    @Test
    @DisplayName("chaves diferentes: sobe")
    void chavesDiferentes() {
        assertThat(configuracao.hmacDeCpf(CifraDeCampoTest.CHAVE_2, chaves)).isNotNull();
        assertThat(configuracao.cifraDeCampo(chaves)).isNotNull();
        assertThat(configuracao
                        .chavesDeCifra("k1", "k1:" + CifraDeCampoTest.CHAVE_1)
                        .kidAtual())
                .isEqualTo("k1");
    }
}
