package br.com.coe.servicos.compartilhado.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** {@code %m} do Logback com a máscara de dado pessoal ({@link MascaraDeLog}) aplicada (CORE-11). */
public class MensagemMascarada extends MessageConverter {

    @Override
    public String convert(ILoggingEvent evento) {
        String mensagem = MascaraDeLog.mascarar(super.convert(evento));
        // quebra de linha numa mensagem forjaria uma linha de log no texto (injeção): vira \\n literal
        return mensagem == null ? null : mensagem.replace("\r", "\\r").replace("\n", "\\n");
    }
}
