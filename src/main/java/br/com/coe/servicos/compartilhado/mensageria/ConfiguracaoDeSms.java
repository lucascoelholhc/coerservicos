package br.com.coe.servicos.compartilhado.mensageria;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fail fast: sem um {@link EnviadorSms} (provedor real A DEFINIR), a aplicação não sobe; o SMS falso
 * do perfil local (código no log) também não sobe junto com o perfil prod.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeSms {

    ConfiguracaoDeSms(ObjectProvider<EnviadorSms> enviadores, Environment ambiente) {
        EnviadorSms enviador = enviadores.getIfAvailable();
        if (enviador == null) {
            throw new IllegalStateException("Provedor de SMS não configurado: nenhum EnviadorSms disponível"
                    + " (provedor A DEFINIR; no desenvolvimento, use o perfil local).");
        }
        if (enviador instanceof EnviadorSmsNoLog && ambiente.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "O SMS falso (código no log) não pode subir com o perfil prod: use só o perfil local.");
        }
    }
}
