package br.com.coe.servicos.compartilhado.log;

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

    @org.junit.jupiter.api.Test
    void cpf() {
        assertThat(Mascara.cpf("123.456.789-09")).isEqualTo("***.***.***-09");
        assertThat(Mascara.cpf("12345678909")).isEqualTo("***.***.***-09");
        assertThat(Mascara.cpf("123")).isEqualTo("***");
        assertThat(Mascara.cpf(null)).isEqualTo("***");
    }

    @org.junit.jupiter.api.Test
    void cep() {
        assertThat(Mascara.cep("89010-000")).isEqualTo("890**-***");
        assertThat(Mascara.cep("8901")).isEqualTo("***");
        assertThat(Mascara.cep(null)).isEqualTo("***");
    }

    @org.junit.jupiter.api.Test
    void tokenECodigo() {
        assertThat(Mascara.token("Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg"))
                .isEqualTo("Rq4Z***");
        assertThat(Mascara.token("curto")).isEqualTo("***");
        assertThat(Mascara.token(null)).isEqualTo("***");
        assertThat(Mascara.codigo("483920")).isEqualTo("******");
    }
}
