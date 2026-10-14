package br.com.coe.servicos.compartilhado.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.log.Mascara;

/**
 * SMS falso do perfil local: escreve o texto (com o código) no log, com o celular mascarado. É assim
 * que o ADMIN do seed entra no local. Só existe no perfil {@code local}.
 */
@Component
@Profile("local")
public class EnviadorSmsNoLog implements EnviadorSms {

    private static final Logger LOG = LoggerFactory.getLogger("coe.sms-falso");

    @Override
    public void enviar(String celular, String texto) {
        LOG.warn("SMS FALSO (perfil local) para {}: {}", Mascara.celular(celular), texto);
    }
}
