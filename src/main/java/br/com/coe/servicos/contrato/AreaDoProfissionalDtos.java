package br.com.coe.servicos.contrato;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** Respostas da área do profissional (FE-06). Valores em {@link Dinheiro} (texto "180.00" no JSON). */
final class AreaDoProfissionalDtos {

    private AreaDoProfissionalDtos() {}

    record Cidade(String nome, String uf) {}

    /**
     * Uma diária do profissional. {@code repasse}: o que ele recebe pelo dia, com a comissão e a taxa congeladas no
     * contrato. {@code cliente} em "Primeiro S."; endereço e celular só no detalhe do contrato, depois do pagamento.
     */
    record Diaria(
            UUID id,
            UUID contratoId,
            long numero,
            LocalDate data,
            String tipo,
            String status,
            Dinheiro repasse,
            String cliente,
            Cidade cidade,
            List<String> servicos,
            Instant chegouEm,
            Instant terminouEm,
            Instant autoLiberaEm) {}

    record Diarias(List<Diaria> itens) {}

    /**
     * {@code liberado}: saldo da conta do profissional no livro-caixa. {@code vaiCair}: repasse das diárias já pagas
     * e ainda não liberadas. {@code emReclamacao}: repasse das diárias contestadas.
     */
    record Saldo(Dinheiro liberado, Dinheiro vaiCair, Dinheiro emReclamacao) {}

    /** {@code diasDaSemana}: 0 = domingo … 6 = sábado. {@code folgas}: de hoje até {@code ate}. */
    record Agenda(List<Integer> diasDaSemana, List<LocalDate> folgas, LocalDate ate) {}
}
