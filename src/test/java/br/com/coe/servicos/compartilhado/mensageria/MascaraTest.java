package br.com.coe.servicos.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MascaraTest {

    @Test
    void celular() {
        assertThat(Mascara.celular("47900000101")).isEqualTo("47*****0101");
        assertThat(Mascara.celular("4790000010")).isEqualTo("47****0010");
        assertThat(Mascara.celular(null)).isEqualTo("(sem celular)");
        assertThat(Mascara.celular("123")).isEqualTo("***");
    }

    @Test
    void email() {
        assertThat(Mascara.email("ana.silva@exemplo.com")).isEqualTo("a***@exemplo.com");
        assertThat(Mascara.email(null)).isEqualTo("(sem e-mail)");
        assertThat(Mascara.email("sem-arroba")).isEqualTo("***");
    }
}
