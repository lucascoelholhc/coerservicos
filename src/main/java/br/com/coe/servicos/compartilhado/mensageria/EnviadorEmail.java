package br.com.coe.servicos.compartilhado.mensageria;

/**
 * Envio de e-mail em texto simples (pt-BR, sem HTML, scripts nem imagens externas). SMTP no local
 * (Mailpit) e em prod ({@code spring.mail.host}); nos testes, um fake em memória.
 */
public interface EnviadorEmail {

    /** Nunca registre o texto: ele leva o link com o token. */
    void enviar(String para, String assunto, String texto);
}
