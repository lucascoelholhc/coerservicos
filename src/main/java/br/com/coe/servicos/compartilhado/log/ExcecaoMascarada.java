package br.com.coe.servicos.compartilhado.log;

import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Pilha de exceção do Logback (o {@code %wEx} do Spring) com a máscara só nas mensagens da exceção e das
 * causas; as linhas "at ..." ficam intactas para a pilha continuar legível (CORE-11).
 */
public class ExcecaoMascarada extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy excecao) {
        return MascaraDeLog.mascararPilha(super.throwableProxyToString(excecao));
    }
}
