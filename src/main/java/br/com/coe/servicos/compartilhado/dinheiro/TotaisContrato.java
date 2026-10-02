package br.com.coe.servicos.compartilhado.dinheiro;

import java.util.List;

/**
 * Totais de um contrato: a soma das diárias calculadas uma a uma, o que bate com o
 * {@code ck_contrato_total} do banco (total = serviços + comissão quando a taxa é do cliente).
 */
public record TotaisContrato(
        Dinheiro valorServicos,
        Dinheiro comissao,
        Dinheiro totalCliente,
        Dinheiro repasseProfissional,
        Dinheiro valorReembolso) {

    public static TotaisContrato somar(List<ValoresDiaria> diarias) {
        if (diarias.isEmpty()) {
            throw new IllegalArgumentException("Contrato sem diárias");
        }
        Dinheiro servicos = Dinheiro.ZERO;
        Dinheiro comissao = Dinheiro.ZERO;
        Dinheiro total = Dinheiro.ZERO;
        Dinheiro repasse = Dinheiro.ZERO;
        Dinheiro reembolso = Dinheiro.ZERO;
        for (ValoresDiaria diaria : diarias) {
            servicos = servicos.somar(diaria.valorServico());
            comissao = comissao.somar(diaria.comissao());
            total = total.somar(diaria.totalCliente());
            repasse = repasse.somar(diaria.repasseProfissional());
            reembolso = reembolso.somar(diaria.valorReembolso());
        }
        return new TotaisContrato(servicos, comissao, total, repasse, reembolso);
    }
}
