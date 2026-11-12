package br.com.coe.servicos.pagamento;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * O gateway de pagamento (PA02: Asaas em conta única da COE, decisão de 11/11). O cliente paga na conta da COE (uma
 * cobrança por contrato); a COE repassa ao profissional por Pix na liberação de cada diária e estorna o que precisa
 * voltar. Até o adaptador real (AWS-09), só o {@link GatewayFalso}, com nomes, estados e eventos no formato do Asaas;
 * a migração futura para subcontas troca só o adaptador. Nenhuma chamada sai da COE com dado de cartão.
 */
public interface GatewayPagamento {

    /** Nome gravado em {@code cobranca.gateway} e {@code evento_gateway.gateway} ("falso", "asaas"). */
    String nome();

    /** Cria a cobrança; {@code referencia} é o id do contrato (Asaas: {@code externalReference}). */
    CobrancaNoGateway criarCobranca(NovaCobranca pedido);

    /** Situação atual da cobrança no gateway. */
    CobrancaNoGateway consultar(String idExterno);

    /**
     * Transferência Pix da conta da COE para a chave do profissional (Asaas: {@code POST /transfers}). A
     * {@code referencia} é o id da diária ({@code externalReference}): antes de mandar de novo, consulte por ela.
     */
    TransferenciaNoGateway transferirPix(NovaTransferencia pedido);

    /** A transferência mais recente com essa referência (Asaas: {@code GET /transfers?externalReference=}). */
    Optional<TransferenciaNoGateway> transferenciaPorReferencia(String referencia);

    /**
     * Estorno (total ou parcial) de uma cobrança paga (Asaas: {@code POST /payments/{id}/refund} com {@code value}).
     * A {@code referencia} (id da diária ou da devolução) vai na descrição do estorno.
     */
    EstornoNoGateway estornar(String cobrancaExterna, Dinheiro valor, String referencia);

    /** Saldo da conta da COE no gateway (Asaas: {@code GET /finance/balance}), para a conciliação diária. */
    Dinheiro saldo();

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

    /** Tipo da chave Pix como o Asaas recebe ({@code pixAddressKeyType}). */
    enum TipoChave {
        CPF("cpf"),
        EMAIL("email"),
        PHONE("celular"),
        EVP("aleatoria");

        private final String codigoNaCoe;

        TipoChave(String codigoNaCoe) {
            this.codigoNaCoe = codigoNaCoe;
        }

        public static TipoChave daCoe(String codigo) {
            for (TipoChave tipo : values()) {
                if (tipo.codigoNaCoe.equals(codigo)) {
                    return tipo;
                }
            }
            throw new IllegalArgumentException("Tipo de chave Pix desconhecido: " + codigo);
        }
    }

    /** A chave em claro só existe aqui, na hora do envio; nunca em log ({@code toString} mascarado). */
    record NovaTransferencia(String referencia, Dinheiro valor, TipoChave tipo, String chave, String descricao) {

        @Override
        public String toString() {
            return "NovaTransferencia[referencia=" + referencia + ", valor=" + valor + ", tipo=" + tipo
                    + ", chave=***]";
        }
    }

    /**
     * A transferência como o gateway a vê. {@code status} no vocabulário do Asaas ({@code PENDING},
     * {@code BANK_PROCESSING}, {@code DONE}, {@code FAILED}, {@code CANCELLED}); {@code motivoFalha} é o código do
     * motivo, sem dado pessoal.
     */
    record TransferenciaNoGateway(
            String idExterno, String referencia, String status, Dinheiro valor, String motivoFalha) {

        public boolean concluida() {
            return "DONE".equals(status);
        }

        public boolean falhou() {
            return "FAILED".equals(status) || "CANCELLED".equals(status);
        }
    }

    /** O estorno como o gateway o vê ({@code status}: {@code PENDING}, {@code DONE}, {@code FAILED}). */
    record EstornoNoGateway(String idExterno, String status) {}

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
