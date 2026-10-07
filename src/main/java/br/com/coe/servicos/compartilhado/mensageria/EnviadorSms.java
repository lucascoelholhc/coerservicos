package br.com.coe.servicos.compartilhado.mensageria;

/**
 * Envio de SMS. Provedor real A DEFINIR (sugestão: Amazon SNS); no perfil local, {@link
 * EnviadorSmsNoLog}; nos testes, um fake em memória. Sem nenhum, a aplicação não sobe.
 */
public interface EnviadorSms {

    /** Envia o texto ao celular (só dígitos, com DDD). Nunca registre o texto: ele leva o código. */
    void enviar(String celular, String texto);
}
