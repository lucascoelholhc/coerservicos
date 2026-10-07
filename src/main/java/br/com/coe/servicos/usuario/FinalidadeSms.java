package br.com.coe.servicos.usuario;

/** Para que serve o código SMS (coluna codigo_sms.finalidade, V14). */
enum FinalidadeSms {
    LOGIN("login"),
    VERIFICAR_CELULAR("verificar_celular"),
    TROCAR_CELULAR("trocar_celular"),
    /** Segundo passo do login, sempre com desafio. */
    MFA("mfa"),
    /** Ligar ou desligar o MFA. */
    CONFIGURAR_MFA("configurar_mfa"),
    COMPROVAR_POSSE("comprovar_posse"),
    RECUPERAR_SENHA("recuperar_senha");

    private final String valor;

    FinalidadeSms(String valor) {
        this.valor = valor;
    }

    String valor() {
        return valor;
    }
}
