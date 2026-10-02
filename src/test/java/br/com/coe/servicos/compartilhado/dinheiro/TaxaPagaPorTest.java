package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaxaPagaPorTest {

    @Test
    @DisplayName("converte de e para o código gravado no banco")
    void codigoDoBanco() {
        assertThat(TaxaPagaPor.deCodigo("cliente")).isEqualTo(TaxaPagaPor.CLIENTE);
        assertThat(TaxaPagaPor.deCodigo("profissional")).isEqualTo(TaxaPagaPor.PROFISSIONAL);
        assertThat(TaxaPagaPor.CLIENTE.codigo()).isEqualTo("cliente");
    }

    @Test
    @DisplayName("recusa código desconhecido")
    void recusaDesconhecido() {
        assertThatIllegalArgumentException().isThrownBy(() -> TaxaPagaPor.deCodigo("ninguem"));
    }
}
