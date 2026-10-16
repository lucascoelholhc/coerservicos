package br.com.coe.servicos.compartilhado.armazenamento;

/**
 * De onde vem o arquivo. Vira o prefixo da chave no bucket e escolhe o limite de tamanho; uma por tabela
 * com {@code chave_arquivo} (documento_verificacao, foto_portfolio, foto_pedido, evidencia_diaria,
 * anexo_disputa; a selfie fica na documento_verificacao, com prefixo próprio).
 */
public enum Categoria {
    DOCUMENTO("documento"),
    SELFIE("selfie"),
    PORTFOLIO("portfolio"),
    PEDIDO("pedido"),
    EVIDENCIA("evidencia"),
    DISPUTA("disputa");

    private final String prefixo;

    Categoria(String prefixo) {
        this.prefixo = prefixo;
    }

    /** Prefixo da chave no bucket (ex.: {@code documento/}), também o nome da categoria na configuração. */
    public String prefixo() {
        return prefixo;
    }
}
