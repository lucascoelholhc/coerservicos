package br.com.coe.servicos.compartilhado.dinheiro;

import java.util.Objects;

/**
 * Cálculo do dinheiro de uma diária (RN31, RN34, PA05, PA07). A comissão é sempre calculada por
 * diária, nunca sobre o total do contrato; o reembolso devolve exatamente o que o cliente pagou.
 */
public final class CalculadoraDiaria {

    private CalculadoraDiaria() {}

    public static ValoresDiaria calcular(Dinheiro valorServico, Percentual comissao, TaxaPagaPor taxaPagaPor) {
        Objects.requireNonNull(valorServico, "valorServico");
        Objects.requireNonNull(comissao, "comissao");
        Objects.requireNonNull(taxaPagaPor, "taxaPagaPor");
        Dinheiro valorComissao = valorServico.multiplicar(comissao);
        Dinheiro totalCliente = switch (taxaPagaPor) {
            case CLIENTE -> valorServico.somar(valorComissao);
            case PROFISSIONAL -> valorServico;
        };
        Dinheiro repasse = switch (taxaPagaPor) {
            case CLIENTE -> valorServico;
            case PROFISSIONAL -> valorServico.subtrair(valorComissao);
        };
        return new ValoresDiaria(valorServico, valorComissao, totalCliente, repasse, totalCliente);
    }
}
