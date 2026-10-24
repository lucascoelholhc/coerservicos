package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN51: mesma diarista + mesmo cliente, no máximo N diárias em qualquer janela de 7 dias seguidos (não semana fixa). */
class JanelaLc150Test {

    // segunda-feira, 2 de novembro de 2026
    private static final LocalDate SEG = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("duas diárias na mesma janela com limite 2: passa")
    void duasPassam() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG), List.of(SEG.plusDays(2)), 2))
                .isEmpty();
    }

    @Test
    @DisplayName("terceira diária dentro de 7 dias seguidos: recusada (devolve o dia que estoura)")
    void terceiraRecusada() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG, SEG.plusDays(1)), List.of(SEG.plusDays(3)), 2))
                .contains(SEG.plusDays(3));
    }

    @Test
    @DisplayName("janela móvel: sexta e sábado já contratados + segunda seguinte = 3 em 7 dias, recusada")
    void virandoASemana() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG.plusDays(4), SEG.plusDays(5)), List.of(SEG.plusDays(7)), 2))
                .contains(SEG.plusDays(7));
    }

    @Test
    @DisplayName("exatos 7 dias depois já é outra janela: passa")
    void seteDiasDepois() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG, SEG.plusDays(1)), List.of(SEG.plusDays(7)), 2))
                .isEmpty();
    }

    @Test
    @DisplayName("três novas no mesmo pedido, sem contratos antes: recusada")
    void tresNoMesmoPedido() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(), List.of(SEG, SEG.plusDays(2), SEG.plusDays(4)), 2))
                .contains(SEG.plusDays(4));
    }

    @Test
    @DisplayName("contratos antigos fora da janela não contam; nada novo, nada a recusar")
    void foraDaJanela() {
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG.minusDays(10), SEG.minusDays(9)), List.of(SEG), 2))
                .isEmpty();
        assertThat(JanelaLc150.primeiroExcesso(List.of(SEG, SEG.plusDays(1), SEG.plusDays(2)), List.of(), 2))
                .isEmpty();
    }
}
