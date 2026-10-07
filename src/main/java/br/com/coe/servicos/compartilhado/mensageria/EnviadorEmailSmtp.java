package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** E-mail em texto simples pelo SMTP configurado ({@code spring.mail.*}); remetente obrigatório. */
public class EnviadorEmailSmtp implements EnviadorEmail {

    private final JavaMailSender correio;
    private final String remetente;

    EnviadorEmailSmtp(JavaMailSender correio, String remetente) {
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalStateException(
                    "Com SMTP configurado, coe.email.remetente é obrigatório (COE_EMAIL_REMETENTE).");
        }
        this.correio = correio;
        this.remetente = remetente.strip();
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        correio.send(mensagem);
    }
}
