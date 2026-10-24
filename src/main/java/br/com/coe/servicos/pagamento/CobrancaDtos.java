package br.com.coe.servicos.pagamento;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** Pedidos e respostas da cobrança do contrato (DOM-07). */
final class CobrancaDtos {

    private CobrancaDtos() {}

    record NovoPagamento(
            @NotNull @Pattern(regexp = "pix|cartao") String metodo) {}

    /** {@code status}: pendente, confirmada, falhou, expirada ou estornada (vocabulário da COE, não do gateway). */
    record Pagamento(
            UUID cobrancaId,
            String metodo,
            String status,
            Dinheiro valor,
            String pixCopiaECola,
            String linkPagamento,
            Instant expiraEm,
            /** O gateway é o falso (local e test): o front mostra "Simular pagamento". Sempre falso no prod. */
            boolean simulavel) {}

    /** O pagamento e se ele acabou de ser criado (201) ou já existia (200). */
    record Resultado(Pagamento pagamento, boolean criado) {}
}
