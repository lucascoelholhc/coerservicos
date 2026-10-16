package br.com.coe.servicos.profissional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pedidos do admin na verificação (DOM-03). O motivo vai por SMS: curto e em português simples. */
final class PedidosDaVerificacao {

    static final int MOTIVO_MAXIMO = 160;

    private PedidosDaVerificacao() {}

    /** O checklist da RN12: cada item conferido pelo admin. */
    record Itens(
            @NotNull Boolean cpf,
            @NotNull Boolean documento,
            @NotNull Boolean selfie,
            @NotNull Boolean fotos,
            @NotNull Boolean texto) {

        boolean todos() {
            return cpf && documento && selfie && fotos && texto;
        }
    }

    /** {@code nr10} só quando há certificado em análise: verificado dá o selo (RN14), recusado pede motivo. */
    record Aprovacao(
            @NotNull @Valid Itens itens,
            @Pattern(regexp = "verificado|recusado") String nr10,

            @Size(max = MOTIVO_MAXIMO) @Pattern(regexp = "[^\\p{Cntrl}]*")
            String motivoNr10) {}

    /** Uma linha só, sem caractere de controle (vai por SMS); a censura de contato roda no serviço. */
    record Motivo(
            @NotBlank @Size(max = MOTIVO_MAXIMO) @Pattern(regexp = "[^\\p{Cntrl}]*")
            String motivo) {}
}
