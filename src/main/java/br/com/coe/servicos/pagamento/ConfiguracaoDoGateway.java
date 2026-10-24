package br.com.coe.servicos.pagamento;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fail fast: sem um {@link GatewayPagamento} (o adaptador do Asaas entra na AWS-09), a aplicação não sobe; o gateway
 * falso (local e test) também não sobe com o perfil prod.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDoGateway {

    private static final int TOKEN_MINIMO_NO_PROD = 32;

    ConfiguracaoDoGateway(ObjectProvider<GatewayPagamento> gateways, Environment ambiente) {
        GatewayPagamento gateway = gateways.getIfAvailable();
        if (gateway == null) {
            throw new IllegalStateException("Gateway de pagamento não configurado: nenhum GatewayPagamento disponível"
                    + " (o adaptador do Asaas entra na AWS-09; no desenvolvimento, use o perfil local).");
        }
        if (gateway instanceof GatewayFalso && ambiente.matchesProfiles("prod")) {
            throw new IllegalStateException("O gateway falso não pode subir com o perfil prod.");
        }
        String token = ambiente.getProperty("coe.gateway.token-webhook", "");
        if (token.isBlank()) {
            throw new IllegalStateException("Token do webhook do gateway ausente (COE_GATEWAY_TOKEN_WEBHOOK).");
        }
        if (ambiente.matchesProfiles("prod") && token.length() < TOKEN_MINIMO_NO_PROD) {
            throw new IllegalStateException("Token do webhook do gateway curto demais para o prod: "
                    + TOKEN_MINIMO_NO_PROD + " caracteres ou mais.");
        }
    }
}
