package br.com.coe.servicos.disputa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** Pedidos e respostas da reclamação (DOM-09). */
final class DisputaDtos {

    private DisputaDtos() {}

    /** RN42: motivo e descrição de 10+ letras. */
    record NovaReclamacao(
            @NotNull UUID diariaId,

            @NotNull @Pattern(regexp = "nao_compareceu|servico_incompleto|qualidade|dano|outro")
            String motivo,

            @NotBlank @Size(min = 10, max = 1000) String descricao) {}

    record Resposta(@NotBlank @Size(min = 2, max = 1000) String texto) {}

    /** RN44b: depois da falta confirmada, o cliente mantém ou cancela as outras diárias pagas. */
    record DemaisDiarias(
            @NotNull @Pattern(regexp = "manter|cancelar") String acao) {}

    record Decisao(
            @NotNull @Pattern(regexp = "cliente|profissional")
            String favor,

            @NotBlank @Size(min = 10, max = 500) String justificativa) {}

    /**
     * A reclamação como os dois lados e o admin a veem. {@code valorDoDia}: o que o cliente pagou pela diária (o que
     * volta se a decisão for a favor dele). Nomes em "Primeiro S.".
     */
    record Disputa(
            UUID id,
            UUID diariaId,
            UUID contratoId,
            LocalDate data,
            String motivo,
            String descricao,
            String status,
            String respostaProfissional,
            Instant respondidaEm,
            Instant prazoDecisaoEm,
            String favor,
            String justificativa,
            Instant decididaEm,
            String demaisDiarias,
            boolean podeEscolherDemais,
            Dinheiro valorDoDia,
            String cliente,
            String profissional,
            Instant criadoEm) {}

    record ItemDaFila(
            UUID id,
            UUID diariaId,
            LocalDate data,
            String motivo,
            String status,
            Instant prazoDecisaoEm,
            boolean respondida,
            String cliente,
            String profissional) {}

    record Fila(List<ItemDaFila> itens, boolean temMais) {}
}
