package br.com.coe.servicos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaves do JWT (PA03), lidas de variável de ambiente: {@code COE_JWT_KID_ATUAL} e
 * {@code COE_JWT_CHAVES} no formato {@code kid:base64,kid:base64} (atual + anterior, para rotação).
 */
@ConfigurationProperties("coe.jwt")
public record PropriedadesJwt(String kidAtual, String chaves) {

    /** Nunca mostra as chaves (nem em log de depuração da configuração). */
    @Override
    public String toString() {
        return "PropriedadesJwt[kidAtual=" + kidAtual + ", chaves=***]";
    }
}
