package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.IntegracaoTest;

/** Token de acesso (PA03): HS256 com kid, 15 min, só id e papéis; validado pelo resource server. */
class TokenDeAcessoTest extends IntegracaoTest {

    private static final String ROTA_PROTEGIDA = "/api/qualquer-coisa"; // não existe: com token válido, 404

    @Autowired
    EmissorDeToken emissor;

    @Autowired
    JsonMapper json;

    @Value("${coe.jwt.chaves}")
    String chavesDeTeste;

    private byte[] chave(String kid) {
        for (String par : chavesDeTeste.split(",")) {
            String[] partes = par.strip().split(":", 2);
            if (partes[0].equals(kid)) {
                return Base64.getDecoder().decode(partes[1]);
            }
        }
        throw new IllegalArgumentException(kid);
    }

    private String assinar(String kid, byte[] chave, JWTClaimsSet claims) throws Exception {
        JWSHeader.Builder cabecalho = new JWSHeader.Builder(JWSAlgorithm.HS256);
        if (kid != null) {
            cabecalho.keyID(kid);
        }
        SignedJWT jwt = new SignedJWT(cabecalho.build(), claims);
        jwt.sign(new MACSigner(chave));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder claimsValidas() {
        Instant agora = relogio.instant();
        return new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("coe-servicos")
                .audience("coe-api")
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(agora.plus(Duration.ofMinutes(15))))
                .jwtID(UUID.randomUUID().toString())
                .claim("papeis", List.of("CLIENTE"));
    }

    private void comToken(String token, int status) throws Exception {
        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andExpect(status().is(status));
    }

    @Test
    @DisplayName("token emitido: HS256, kid atual, 15 min, só id e papéis (nada de celular, e-mail ou nome)")
    void conteudoDoToken() throws Exception {
        UUID id = UUID.randomUUID();
        TokenDeAcesso token = emissor.emitir(id, Set.of(Papel.CLIENTE, Papel.PROFISSIONAL));

        String[] partes = token.valor().split("\\.");
        @SuppressWarnings("unchecked")
        Map<String, Object> cabecalho = json.readValue(Base64.getUrlDecoder().decode(partes[0]), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = json.readValue(Base64.getUrlDecoder().decode(partes[1]), Map.class);

        assertThat(cabecalho).containsEntry("alg", "HS256").containsEntry("kid", "teste-2");
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "papeis", "iss", "aud", "iat", "exp", "jti");
        assertThat(claims).containsEntry("sub", id.toString()).containsEntry("iss", "coe-servicos");
        assertThat(claims.get("papeis")).isEqualTo(List.of("CLIENTE", "PROFISSIONAL"));
        assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue())
                .isEqualTo(900);
        assertThat(token.expiraEm()).isEqualTo(relogio.instant().plus(Duration.ofMinutes(15)));
        assertThat(new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8))
                .doesNotContain("celular", "email", "nome");
    }

    @Test
    @DisplayName("com token válido a rota protegida atende (404 = passou da segurança)")
    void tokenValido() throws Exception {
        comToken(emissor.emitir(UUID.randomUUID(), Set.of(Papel.CLIENTE)).valor(), 404);
    }

    @Test
    @DisplayName("token assinado com a chave anterior ainda vale (rotação)")
    void chaveAnterior() throws Exception {
        comToken(assinar("teste-1", chave("teste-1"), claimsValidas().build()), 404);
    }

    @Test
    @DisplayName("tolerância de 30 s no vencimento")
    void tolerancia() throws Exception {
        String token = emissor.emitir(UUID.randomUUID(), Set.of(Papel.CLIENTE)).valor();
        relogio.avancar(Duration.ofMinutes(15).plusSeconds(20));

        comToken(token, 404);
    }

    @Test
    @DisplayName("token vencido: 401 em Problem Details com WWW-Authenticate")
    void vencido() throws Exception {
        String token = emissor.emitir(UUID.randomUUID(), Set.of(Papel.CLIENTE)).valor();
        relogio.avancar(Duration.ofMinutes(15).plusSeconds(31));

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-autenticado"));
    }

    @Test
    @DisplayName("assinatura com outra chave: 401")
    void assinaturaErrada() throws Exception {
        byte[] outra = new byte[32];
        new java.security.SecureRandom().nextBytes(outra);

        comToken(assinar("teste-2", outra, claimsValidas().build()), 401);
    }

    @Test
    @DisplayName("kid desconhecido ou ausente: 401")
    void kidDesconhecido() throws Exception {
        comToken(assinar("teste-9", chave("teste-2"), claimsValidas().build()), 401);
        comToken(assinar(null, chave("teste-2"), claimsValidas().build()), 401);
    }

    @Test
    @DisplayName("aud ou iss errados: 401")
    void audienciaOuEmissorErrados() throws Exception {
        comToken(
                assinar(
                        "teste-2",
                        chave("teste-2"),
                        claimsValidas().audience("outra-api").build()),
                401);
        comToken(
                assinar(
                        "teste-2",
                        chave("teste-2"),
                        claimsValidas().issuer("outro").build()),
                401);
    }

    @Test
    @DisplayName("sem sub, ou sub que não é UUID: 401 (nunca 500)")
    void subInvalido() throws Exception {
        comToken(
                assinar(
                        "teste-2",
                        chave("teste-2"),
                        claimsValidas().subject(null).build()),
                401);
        comToken(
                assinar(
                        "teste-2",
                        chave("teste-2"),
                        claimsValidas().subject("47900000001").build()),
                401);
    }

    @Test
    @DisplayName("/api/admin/** só para ADMIN: 403 para os outros papéis, passa para o ADMIN")
    void rotasDoAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/qualquer-coisa")
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:proibido"));
        mockMvc.perform(get("/api/admin/qualquer-coisa")
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem exp: 401")
    void semVencimento() throws Exception {
        comToken(
                assinar(
                        "teste-2",
                        chave("teste-2"),
                        claimsValidas().expirationTime(null).build()),
                401);
    }

    @Test
    @DisplayName("token sem assinatura (alg none) ou lixo: 401")
    void semAssinatura() throws Exception {
        String claims = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(claimsValidas().build().toString().getBytes(StandardCharsets.UTF_8));
        String semAssinatura = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + claims + ".";

        comToken(semAssinatura, 401);
        comToken("isso-nao-e-um-jwt", 401);
    }
}
