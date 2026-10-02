package br.com.coe.servicos.compartilhado.dinheiro;

/** Quem paga a comissão da COE. Decidido: o cliente (PA05); o profissional fica para contratos antigos/futuros. */
public enum TaxaPagaPor {
    CLIENTE("cliente"),
    PROFISSIONAL("profissional");

    private final String codigo;

    TaxaPagaPor(String codigo) {
        this.codigo = codigo;
    }

    /** O valor gravado no banco (contrato.taxa_paga_por, configuracao TAXA_PAGA_POR). */
    public String codigo() {
        return codigo;
    }

    public static TaxaPagaPor deCodigo(String codigo) {
        for (TaxaPagaPor taxa : values()) {
            if (taxa.codigo.equals(codigo)) {
                return taxa;
            }
        }
        throw new IllegalArgumentException("Quem paga a taxa desconhecido: " + codigo);
    }
}
