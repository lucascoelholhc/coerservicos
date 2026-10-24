package br.com.coe.servicos.pagamento;

import java.time.Instant;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * O gateway de pagamento com custódia (PA02 decidido: Asaas; modalidade de custódia A DEFINIR com o comercial).
 * Até o adaptador real (AWS-09), só o {@link GatewayFalso}, com nomes, estados e eventos no formato do Asaas, para a
 * troca ser só de adaptador. Nenhuma chamada sai da COE com dado de cartão: cartão é pago na página do gateway.
 */
public interface GatewayPagamento {

    /** Nome gravado em {@code cobranca.gateway} e {@code evento_gateway.gateway} ("falso", "asaas"). */
    String nome();

    /** Cria a cobrança; {@code referencia} é o id da cobrança na COE (Asaas: {@code externalReference}). */
    CobrancaNoGateway criarCobranca(NovaCobranca pedido);

    /** Situação atual da cobrança no gateway. */
    CobrancaNoGateway consultar(String idExterno);

    enum Metodo {
        /** Asaas: {@code billingType = PIX}. */
        PIX("pix"),
        /** Asaas: {@code billingType = CREDIT_CARD}, pago na página da cobrança ({@code invoiceUrl}). */
        CARTAO("cartao");

        private final String codigo;

        Metodo(String codigo) {
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }

        public static Metodo doCodigo(String codigo) {
            for (Metodo metodo : values()) {
                if (metodo.codigo.equals(codigo)) {
                    return metodo;
                }
            }
            throw new IllegalArgumentException("Método de pagamento desconhecido: " + codigo);
        }
    }

    record NovaCobranca(UUID referencia, Dinheiro valor, Metodo metodo, Instant vencimento, String descricao) {}

    /**
     * A cobrança como o gateway a vê. {@code status} no vocabulário do Asaas ({@code PENDING}, {@code RECEIVED},
     * {@code CONFIRMED}, {@code OVERDUE}, {@code REFUNDED}...).
     */
    record CobrancaNoGateway(
            String idExterno,
            String status,
            Dinheiro valor,
            String linkPagamento,
            String pixCopiaECola,
            Instant expiraEm) {}
}
