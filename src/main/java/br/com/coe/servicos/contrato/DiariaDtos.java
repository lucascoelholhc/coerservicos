package br.com.coe.servicos.contrato;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.Size;

/** Pedidos e respostas da execução da diária (DOM-08). */
final class DiariaDtos {

    private DiariaDtos() {}

    /** "Terminei o dia": o recado é opcional e passa pela censura de contato (RN36). */
    record Terminei(@Size(max = 300) String recado) {}

    record FotoEnviada(UUID id) {}

    record Diaria(
            UUID id,
            UUID contratoId,
            LocalDate data,
            String tipo,
            String status,
            Instant chegouEm,
            Instant terminouEm,
            Instant autoLiberaEm,
            Instant liberadaEm,
            String liberadaPor) {}
}
