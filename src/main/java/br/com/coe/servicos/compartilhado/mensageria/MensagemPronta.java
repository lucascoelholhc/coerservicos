package br.com.coe.servicos.compartilhado.mensageria;

/**
 * Mensagem com segredo (código, link) pronta para sair depois do commit. Existe só em memória:
 * código e link nunca são gravados em texto no banco (decisão de 07/10).
 */
public record MensagemPronta(Canal canal, String destino, String texto) {

    public enum Canal {
        SMS
    }

    public static MensagemPronta sms(String celular, String texto) {
        return new MensagemPronta(Canal.SMS, celular, texto);
    }

    String destinoMascarado() {
        return Mascara.celular(destino);
    }

    @Override
    public String toString() {
        return "MensagemPronta[canal=" + canal + ", destino=" + destinoMascarado() + ", texto=***]";
    }
}
