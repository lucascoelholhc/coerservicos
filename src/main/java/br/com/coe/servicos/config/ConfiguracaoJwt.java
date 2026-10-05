package br.com.coe.servicos.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * JWT de acesso (PA03): HS256 com kid. Assina com a chave atual; valida com qualquer chave
 * configurada (rotação), exigindo kid, iss, aud e exp, com o {@link Clock} injetado e 30 s de folga.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesJwt.class)
public class ConfiguracaoJwt {

    public static final String EMISSOR = "coe-servicos";
    public static final String AUDIENCIA = "coe-api";
    public static final String CLAIM_PAPEIS = "papeis";

    private static final Duration TOLERANCIA = Duration.ofSeconds(30);

    @Bean
    ChavesJwt chavesJwt(PropriedadesJwt propriedades) {
        return ChavesJwt.de(propriedades.kidAtual(), propriedades.chaves());
    }

    @Bean
    JwtEncoder codificadorJwt(ChavesJwt chaves) {
        JWK atual = jwk(chaves.kidAtual(), chaves.chaveAtual());
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(atual)));
    }

    @Bean
    JwtDecoder decodificadorJwt(ChavesJwt chaves, Clock clock) {
        List<JWK> todas = chaves.todas().entrySet().stream()
                .map(entrada -> jwk(entrada.getKey(), entrada.getValue()))
                .toList();
        JWSKeySelector<SecurityContext> porKid =
                new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, new ImmutableJWKSet<>(new JWKSet(todas)));
        DefaultJWTProcessor<SecurityContext> processador = new DefaultJWTProcessor<>();
        // Sem kid não há chave: nunca tenta todas as chaves às cegas.
        processador.setJWSKeySelector((cabecalho, contexto) ->
                cabecalho.getKeyID() == null ? List.of() : porKid.selectJWSKeys(cabecalho, contexto));
        // As claims são conferidas pelos validadores do Spring abaixo (com o Clock injetado).
        processador.setJWTClaimsSetVerifier((claims, contexto) -> {});

        JwtTimestampValidator tempo = new JwtTimestampValidator(TOLERANCIA);
        tempo.setClock(clock);
        NimbusJwtDecoder decodificador = new NimbusJwtDecoder(processador);
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                tempo,
                new JwtIssuerValidator(EMISSOR),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCIA)),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)));
        return decodificador;
    }

    private static JWK jwk(String kid, SecretKey chave) {
        return new OctetSequenceKey.Builder(chave)
                .keyID(kid)
                .algorithm(JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }
}
