package br.com.coe.servicos.avaliacao;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pedidos e respostas das avaliações (DOM-10). */
final class AvaliacaoDtos {

    private AvaliacaoDtos() {}

    /** RN55: nota de 1 a 5 e comentário opcional (até 500, sem contato). */
    record NovaAvaliacao(
            @NotNull UUID contratoId,
            @NotNull @Min(1) @Max(5) Integer nota,
            @Size(max = 500) String comentario) {}

    record Avaliacao(UUID id, UUID contratoId, int nota, String comentario, Instant criadoEm) {}

    /** O que a tela do contrato precisa: se ainda dá para avaliar e a avaliação já feita por mim. */
    record Situacao(boolean podeAvaliar, Avaliacao minha) {}
}
