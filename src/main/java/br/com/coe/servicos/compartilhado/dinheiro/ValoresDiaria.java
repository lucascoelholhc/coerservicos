package br.com.coe.servicos.compartilhado.dinheiro;

/**
 * Valores de uma diária, calculados por {@link CalculadoraDiaria}.
 *
 * @param valorServico o valor do serviço no dia, definido pelo profissional
 * @param comissao a comissão da COE sobre esta diária
 * @param totalCliente o que o cliente paga por esta diária
 * @param repasseProfissional o que o profissional recebe quando a diária é liberada
 * @param valorReembolso o que volta ao cliente se a diária for reembolsada (igual ao que ele pagou)
 */
public record ValoresDiaria(
        Dinheiro valorServico,
        Dinheiro comissao,
        Dinheiro totalCliente,
        Dinheiro repasseProfissional,
        Dinheiro valorReembolso) {}
