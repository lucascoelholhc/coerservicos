package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/** RN40: a diária só muda pelas transições do diagrama; qualquer outra é erro (100% de cobertura). */
class StatusDiariaTest {

    @Test
    @DisplayName("as transições do diagrama são permitidas")
    void permitidas() {
        assertThat(StatusDiaria.AGENDADA.podeIrPara(StatusDiaria.PAGA)).isTrue();
        assertThat(StatusDiaria.AGENDADA.podeIrPara(StatusDiaria.CANCELADA)).isTrue();
        assertThat(StatusDiaria.PAGA.podeIrPara(StatusDiaria.ANDAMENTO)).isTrue();
        assertThat(StatusDiaria.ANDAMENTO.podeIrPara(StatusDiaria.AGUARDANDO)).isTrue();
        assertThat(StatusDiaria.AGUARDANDO.podeIrPara(StatusDiaria.LIBERADA)).isTrue();
        for (StatusDiaria de : EnumSet.of(StatusDiaria.PAGA, StatusDiaria.ANDAMENTO, StatusDiaria.AGUARDANDO)) {
            assertThat(de.podeIrPara(StatusDiaria.CONTESTADA)).as(de.name()).isTrue();
        }
        assertThat(StatusDiaria.CONTESTADA.podeIrPara(StatusDiaria.LIBERADA)).isTrue();
        assertThat(StatusDiaria.CONTESTADA.podeIrPara(StatusDiaria.REEMBOLSADA)).isTrue();
        assertThat(StatusDiaria.PAGA.podeIrPara(StatusDiaria.REEMBOLSADA)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusDiaria.class,
            names = {"LIBERADA", "REEMBOLSADA", "CANCELADA"})
    @DisplayName("estados finais não vão para lugar nenhum")
    void finais(StatusDiaria finalizado) {
        assertThat(finalizado.finalizado()).isTrue();
        for (StatusDiaria para : StatusDiaria.values()) {
            assertThat(finalizado.podeIrPara(para))
                    .as(finalizado + " -> " + para)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("transições fora do diagrama são recusadas com 422 transicao-invalida")
    void invalidas() {
        Set<String> permitidas = Set.of(
                "AGENDADA>PAGA",
                "AGENDADA>CANCELADA",
                "PAGA>ANDAMENTO",
                "ANDAMENTO>AGUARDANDO",
                "AGUARDANDO>LIBERADA",
                "PAGA>CONTESTADA",
                "ANDAMENTO>CONTESTADA",
                "AGUARDANDO>CONTESTADA",
                "CONTESTADA>LIBERADA",
                "CONTESTADA>REEMBOLSADA",
                // RN44b: depois de uma falta confirmada, o cliente cancela as outras diárias pagas (reembolso integral)
                "PAGA>REEMBOLSADA");
        int recusadas = 0;
        for (StatusDiaria de : StatusDiaria.values()) {
            for (StatusDiaria para : StatusDiaria.values()) {
                if (permitidas.contains(de + ">" + para)) {
                    continue;
                }
                assertThat(de.podeIrPara(para)).as(de + " -> " + para).isFalse();
                assertThatThrownBy(() -> de.exigirIrPara(para))
                        .isInstanceOf(RegraDeNegocioException.class)
                        .extracting("codigo")
                        .isEqualTo("transicao-invalida");
                recusadas++;
            }
        }
        assertThat(recusadas).isEqualTo(64 - permitidas.size());
        StatusDiaria.PAGA.exigirIrPara(StatusDiaria.ANDAMENTO);
    }

    @Test
    @DisplayName("o código do banco vai e volta")
    void codigo() {
        for (StatusDiaria status : StatusDiaria.values()) {
            assertThat(StatusDiaria.doCodigo(status.codigo())).isEqualTo(status);
            assertThat(status.finalizado())
                    .isEqualTo(EnumSet.of(StatusDiaria.LIBERADA, StatusDiaria.REEMBOLSADA, StatusDiaria.CANCELADA)
                            .contains(status));
        }
        assertThat(StatusDiaria.AGUARDANDO.codigo()).isEqualTo("aguardando");
        assertThatThrownBy(() -> StatusDiaria.doCodigo("inventado")).isInstanceOf(IllegalArgumentException.class);
    }
}
