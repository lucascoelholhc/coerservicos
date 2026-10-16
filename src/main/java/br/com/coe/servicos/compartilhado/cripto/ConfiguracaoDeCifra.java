package br.com.coe.servicos.compartilhado.cripto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chaves de cifra e do HMAC do CPF; sem elas (em qualquer perfil), a aplicação não sobe. */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoDeCifra {

    @Bean
    ChavesDeCifra chavesDeCifra(
            @Value("${coe.cifra.kid-atual:}") String kidAtual, @Value("${coe.cifra.chaves:}") String chaves) {
        return ChavesDeCifra.de(kidAtual, chaves);
    }

    @Bean
    CifraDeCampo cifraDeCampo(ChavesDeCifra chaves) {
        return new CifraDeCampo(chaves);
    }

    @Bean
    HmacDeCpf hmacDeCpf(@Value("${coe.cifra.chave-hmac-cpf:}") String chave, ChavesDeCifra chaves) {
        HmacDeCpf hmac = HmacDeCpf.de(chave);
        if (chaves.usa(hmac.bytesDaChave())) {
            throw new IllegalStateException(
                    "Chave do HMAC do CPF inválida: igual a uma chave de cifra (COE_CHAVE_HMAC_CPF)");
        }
        return hmac;
    }
}
