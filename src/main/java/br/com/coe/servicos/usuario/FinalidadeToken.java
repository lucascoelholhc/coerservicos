package br.com.coe.servicos.usuario;

/** Para que serve o token (coluna token_verificacao.finalidade, V14). */
enum FinalidadeToken {
    CONFIRMAR_EMAIL("confirmar_email"),
    LINK_POSSE("link_posse"),
    RECUPERAR_SENHA("recuperar_senha"),
    COMPROVANTE_POSSE("comprovante_posse");

    private final String valor;

    FinalidadeToken(String valor) {
        this.valor = valor;
    }

    String valor() {
        return valor;
    }
}
