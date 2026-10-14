package br.com.coe.servicos.compartilhado.mensageria;

import java.util.Objects;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.log.Mascara;

/**
 * Mensagem com segredo (código, link) pronta para sair depois do commit. Existe só em memória:
 * código e link nunca são gravados em texto no banco (decisão de 07/10). A prioridade escolhe a fila
 * de envio (CORE-07) e a cota aponta a linha que a gerou, para devolver a cota se for descartada.
 */
public record MensagemPronta(
        Canal canal, String destino, String assunto, String texto, Prioridade prioridade, Cota cota) {

    public enum Canal {
        SMS,
        EMAIL
    }

    /** Prioritária: login por código, MFA e recuperação de senha. Normal: prova de posse e confirmações. */
    public enum Prioridade {
        PRIORITARIA,
        NORMAL
    }

    /** Tabela da linha que gerou a mensagem (e contou no limite por destino). */
    public enum Origem {
        CODIGO_SMS,
        TOKEN_VERIFICACAO
    }

    /** A linha que gerou a mensagem: se ela for descartada, essa linha sai das contagens do limite. */
    public record Cota(Origem origem, UUID id) {

        public Cota {
            Objects.requireNonNull(origem, "origem");
            Objects.requireNonNull(id, "id");
        }
    }

    public MensagemPronta {
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(prioridade, "prioridade");
    }

    /** SMS sem linha de cota (prioridade normal). */
    public static MensagemPronta sms(String celular, String texto) {
        return sms(celular, texto, Prioridade.NORMAL, null);
    }

    public static MensagemPronta sms(String celular, String texto, Prioridade prioridade, Cota cota) {
        return new MensagemPronta(Canal.SMS, celular, null, texto, prioridade, cota);
    }

    /** E-mail sem linha de cota (prioridade normal). */
    public static MensagemPronta email(String para, String assunto, String texto) {
        return email(para, assunto, texto, Prioridade.NORMAL, null);
    }

    public static MensagemPronta email(String para, String assunto, String texto, Prioridade prioridade, Cota cota) {
        return new MensagemPronta(Canal.EMAIL, para, assunto, texto, prioridade, cota);
    }

    String destinoMascarado() {
        return canal == Canal.SMS ? Mascara.celular(destino) : Mascara.email(destino);
    }

    @Override
    public String toString() {
        return "MensagemPronta[canal=" + canal + ", destino=" + destinoMascarado() + ", prioridade=" + prioridade
                + ", texto=***]";
    }
}
