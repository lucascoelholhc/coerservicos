package br.com.coe.servicos.mensagem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedidos e respostas do chat (DOM-05). O outro lado aparece só como "Primeiro S." e nenhuma resposta tem contato:
 * ele só circula depois do pagamento (RN45, RN50), e mesmo assim pelo texto que a pessoa escreveu.
 */
final class ChatDtos {

    private ChatDtos() {}

    record AbrirConversa(@NotNull UUID profissionalId) {}

    record NovaMensagem(@NotBlank @Size(max = 2000) String texto) {}

    record Conversa(
            UUID id,
            UUID profissionalId,
            String outroNome,
            boolean souProfissional,
            Instant ultimaMensagemEm,
            int naoLidas,
            boolean contatoLiberado) {}

    record Conversas(List<Conversa> itens) {}

    /** {@code motivos}: as regras quebradas (só aparecem para o autor de uma mensagem bloqueada, RN48). */
    record Mensagem(
            UUID id,
            boolean minha,
            String texto,
            boolean bloqueada,
            List<String> motivos,
            boolean lida,
            Instant criadoEm) {}

    record Mensagens(List<Mensagem> itens) {}

    /** Tentativa de passar contato: qual foi, o limite antes da análise e se a conta já foi para análise (RN49). */
    record Aviso(int tentativa, int limite, boolean contaEmAnalise) {}

    record Envio(Mensagem mensagem, Aviso aviso) {}
}
