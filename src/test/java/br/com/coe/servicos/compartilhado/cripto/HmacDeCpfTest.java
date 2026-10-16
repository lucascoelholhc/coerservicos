package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/** HMAC do CPF (chave própria): unicidade e busca exata sem decifrar. */
class HmacDeCpfTest {

    private static final String CHAVE = "Y2hhdmUtZG8taG1hYy1kby1jcGYtc28tZGUtdGVzdGU=";
    private static final String OUTRA = "b3V0cmEtY2hhdmUtZG8taG1hYy1kby1jcGYtdGVzdGU=";

    @Test
    @DisplayName("estável: o mesmo CPF, com ou sem máscara, dá o mesmo HMAC de 32 bytes")
    void estavel() {
        HmacDeCpf hmac = HmacDeCpf.de(CHAVE);

        byte[] comMascara = hmac.de(Cpf.de("529.982.247-25"));
        byte[] semMascara = hmac.de(Cpf.de("52998224725"));

        assertThat(comMascara).hasSize(32).isEqualTo(semMascara);
        assertThat(hmac.de(Cpf.de("111.444.777-35"))).isNotEqualTo(comMascara);
    }

    @Test
    @DisplayName("outra chave dá outro HMAC (vazamento da tabela não basta para testar CPFs)")
    void outraChave() {
        assertThat(HmacDeCpf.de(CHAVE).de(Cpf.de("52998224725")))
                .isNotEqualTo(HmacDeCpf.de(OUTRA).de(Cpf.de("52998224725")));
    }

    @Test
    @DisplayName("chave ruim não sobe")
    void chaveRuim() {
        assertThatThrownBy(() -> HmacDeCpf.de("")).hasMessageContaining("COE_CHAVE_HMAC_CPF");
        assertThatThrownBy(() -> HmacDeCpf.de("c2VncmVkbw==")).hasMessageContaining("32 bytes");
        assertThat(HmacDeCpf.de(CHAVE).toString()).doesNotContain(CHAVE);
    }
}
