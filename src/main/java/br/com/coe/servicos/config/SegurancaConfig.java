package br.com.coe.servicos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: sem sessão, JWT de acesso no header Authorization (resource server). Públicos só
 * o cadastro de cliente, o login, a renovação e o health. Sem login ou com token inválido → 401;
 * sem permissão → 403, ambos em Problem Details.
 */
@Configuration(proxyBeanMethods = false)
public class SegurancaConfig {

    private static final int CUSTO_BCRYPT = 12;
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Set<String> ROTAS_SEM_BEARER = Set.of("/api/auth/entrar", "/api/auth/renovar");

    @Bean
    SecurityFilterChain cadeiaDaApi(HttpSecurity http, JsonMapper json, JwtDecoder decodificador) throws Exception {
        AuthenticationEntryPoint naoAutenticado = (requisicao, resposta, erro) -> {
            resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            escrever(
                    resposta,
                    json,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "nao-autenticado",
                    "Não autenticado",
                    "Entre na sua conta para continuar.");
        };
        AccessDeniedHandler semPermissao = (requisicao, resposta, erro) -> escrever(
                resposta,
                json,
                HttpServletResponse.SC_FORBIDDEN,
                "proibido",
                "Acesso negado",
                "Você não tem permissão para fazer isso.");
        // API stateless com token no header Authorization: sem cookie de sessão, não há CSRF a
        // proteger. O cookie do refresh (SameSite=Strict, só /api/auth) ganha a checagem de Origin
        // no CORE-08.
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST, "/api/contas/cliente", "/api/auth/entrar", "/api/auth/renovar")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(recurso -> recurso.bearerTokenResolver(tokenForaDoLogin())
                        .jwt(jwt -> jwt.decoder(decodificador).jwtAuthenticationConverter(papeisDoToken()))
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao))
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(naoAutenticado).accessDeniedHandler(semPermissao));
        return http.build();
    }

    /**
     * Login e renovação não leem o header Authorization: o front pode mandar o token vencido junto
     * (é justamente quando renova) e isso não pode barrar a requisição antes do controller.
     */
    private static BearerTokenResolver tokenForaDoLogin() {
        DefaultBearerTokenResolver padrao = new DefaultBearerTokenResolver();
        return requisicao -> ROTAS_SEM_BEARER.contains(requisicao
                        .getRequestURI()
                        .substring(requisicao.getContextPath().length()))
                ? null
                : padrao.resolve(requisicao);
    }

    /** Claim "papeis" vira ROLE_CLIENTE, ROLE_PROFISSIONAL, ROLE_ADMIN; o nome é o id (sub). */
    private static JwtAuthenticationConverter papeisDoToken() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(ConfiguracaoJwt.CLAIM_PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }

    /**
     * Senhas com {bcrypt} e custo 12. Hash sem prefixo (o "$2b$..." do seed local) é conferido como
     * bcrypt e regravado como {bcrypt} no próximo login certo.
     */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(CUSTO_BCRYPT);
        DelegatingPasswordEncoder delegante = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        delegante.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegante;
    }

    private static void escrever(
            HttpServletResponse resposta, JsonMapper json, int status, String codigo, String titulo, String detalhe)
            throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "urn:coe:erro:" + codigo);
        problema.put("title", titulo);
        problema.put("status", status);
        problema.put("detail", detalhe);
        resposta.setStatus(status);
        resposta.setContentType(PROBLEM_JSON);
        resposta.getOutputStream().write(json.writeValueAsString(problema).getBytes(StandardCharsets.UTF_8));
    }
}
