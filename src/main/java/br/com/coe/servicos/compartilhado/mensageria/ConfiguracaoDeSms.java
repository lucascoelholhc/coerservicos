package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/** Fail fast: sem um {@link EnviadorSms} (provedor real A DEFINIR), a aplicação não sobe. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeSms {

    ConfiguracaoDeSms(ObjectProvider<EnviadorSms> enviadores) {
        if (enviadores.getIfAvailable() == null) {
            throw new IllegalStateException("Provedor de SMS não configurado: nenhum EnviadorSms disponível"
                    + " (provedor A DEFINIR; no desenvolvimento, use o perfil local).");
        }
    }
}
