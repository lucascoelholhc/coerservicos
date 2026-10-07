package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.config.ChavesJwt;
import br.com.coe.servicos.config.ConfiguracaoJwt;

/** Emite o token de acesso (PA03): só id, papéis, iss, aud, iat, exp (15 min) e jti. */
@Component
public class EmissorDeToken {

    static final Duration VALIDADE = Duration.ofMinutes(15);

    private final JwtEncoder codificador;
    private final ChavesJwt chaves;
    private final Clock clock;

    EmissorDeToken(JwtEncoder codificador, ChavesJwt chaves, Clock clock) {
        this.codificador = codificador;
        this.chaves = chaves;
        this.clock = clock;
    }

    public TokenDeAcesso emitir(UUID usuarioId, Set<Papel> papeis) {
        return emitir(usuarioId, papeis, false);
    }

    /** Com a marca de contato pendente (RN61), calculada do banco por quem chama. */
    public TokenDeAcesso emitir(UUID usuarioId, Set<Papel> papeis, boolean contatoPendente) {
        Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = agora.plus(VALIDADE);
        List<String> nomesDosPapeis = papeis.stream().map(Papel::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .issuer(ConfiguracaoJwt.EMISSOR)
                .audience(List.of(ConfiguracaoJwt.AUDIENCIA))
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .id(UUID.randomUUID().toString())
                .claim(ConfiguracaoJwt.CLAIM_PAPEIS, nomesDosPapeis)
                .claim(ConfiguracaoJwt.CLAIM_CONTATO_PENDENTE, contatoPendente)
                .build();
        JwsHeader cabecalho =
                JwsHeader.with(MacAlgorithm.HS256).keyId(chaves.kidAtual()).build();
        String valor =
                codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenDeAcesso(valor, expiraEm);
    }
}
