package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Comissão sempre por diária; reembolso = o que o cliente pagou pela diária (RN31, RN34, PA05, PA07). */
class CalculadoraDiariaTest {

    private static final Percentual DEZ_POR_CENTO = Percentual.de("0.10");

    @Test
    @DisplayName("280 / 10% / taxa do cliente: comissão 28, total 308, repasse 280, reembolso 308")
    void taxaDoCliente() {
        ValoresDiaria valores = CalculadoraDiaria.calcular(Dinheiro.de("280"), DEZ_POR_CENTO, TaxaPagaPor.CLIENTE);

        assertThat(valores.valorServico()).isEqualTo(Dinheiro.de("280.00"));
        assertThat(valores.comissao()).isEqualTo(Dinheiro.de("28.00"));
        assertThat(valores.totalCliente()).isEqualTo(Dinheiro.de("308.00"));
        assertThat(valores.repasseProfissional()).isEqualTo(Dinheiro.de("280.00"));
        assertThat(valores.valorReembolso()).isEqualTo(Dinheiro.de("308.00"));
    }

    @Test
    @DisplayName("280 / 10% / taxa do profissional: comissão 28, total 280, repasse 252, reembolso 280")
    void taxaDoProfissional() {
        ValoresDiaria valores = CalculadoraDiaria.calcular(Dinheiro.de("280"), DEZ_POR_CENTO, TaxaPagaPor.PROFISSIONAL);

        assertThat(valores.comissao()).isEqualTo(Dinheiro.de("28.00"));
        assertThat(valores.totalCliente()).isEqualTo(Dinheiro.de("280.00"));
        assertThat(valores.repasseProfissional()).isEqualTo(Dinheiro.de("252.00"));
        assertThat(valores.valorReembolso()).isEqualTo(Dinheiro.de("280.00"));
    }

    @Test
    @DisplayName("comissão de 0%: tudo igual ao valor do serviço")
    void comissaoZero() {
        ValoresDiaria valores = CalculadoraDiaria.calcular(Dinheiro.de("280"), Percentual.ZERO, TaxaPagaPor.CLIENTE);

        assertThat(valores.comissao()).isEqualTo(Dinheiro.ZERO);
        assertThat(valores.totalCliente()).isEqualTo(Dinheiro.de("280.00"));
    }

    @Test
    @DisplayName("3 diárias de 280: os totais do contrato são a soma das diárias calculadas uma a uma")
    void totaisDoContrato() {
        ValoresDiaria diaria = CalculadoraDiaria.calcular(Dinheiro.de("280"), DEZ_POR_CENTO, TaxaPagaPor.CLIENTE);

        TotaisContrato totais = TotaisContrato.somar(List.of(diaria, diaria, diaria));

        assertThat(totais.valorServicos()).isEqualTo(Dinheiro.de("840.00"));
        assertThat(totais.comissao()).isEqualTo(Dinheiro.de("84.00"));
        assertThat(totais.totalCliente()).isEqualTo(Dinheiro.de("924.00"));
        assertThat(totais.repasseProfissional()).isEqualTo(Dinheiro.de("840.00"));
        // o mesmo que o banco exige em ck_contrato_total (taxa do cliente)
        assertThat(totais.totalCliente()).isEqualTo(totais.valorServicos().somar(totais.comissao()));
    }

    @Test
    @DisplayName("comissão é por diária: 3 x 105,05 dá 31,50 (3 x 10,50), não 31,52 (10% de 315,15)")
    void comissaoPorDiariaNaoSobreOTotal() {
        ValoresDiaria diaria = CalculadoraDiaria.calcular(Dinheiro.de("105.05"), DEZ_POR_CENTO, TaxaPagaPor.CLIENTE);

        TotaisContrato totais = TotaisContrato.somar(List.of(diaria, diaria, diaria));

        assertThat(totais.comissao()).isEqualTo(Dinheiro.de("31.50"));
    }

    @Test
    @DisplayName("quem paga a taxa é obrigatório (nulo não vira profissional em silêncio)")
    void taxaPagaPorObrigatoria() {
        assertThatNullPointerException()
                .isThrownBy(() -> CalculadoraDiaria.calcular(Dinheiro.de("280"), DEZ_POR_CENTO, null));
    }

    @Test
    @DisplayName("contrato sem diárias é recusado")
    void contratoSemDiarias() {
        assertThatIllegalArgumentException().isThrownBy(() -> TotaisContrato.somar(List.of()));
    }
}
