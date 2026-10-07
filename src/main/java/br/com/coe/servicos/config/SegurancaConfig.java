package br.com.coe.servicos.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: sem sessão, JWT de acesso no header Authorization (resource server), negar por
 * padrão (todo endpoint tem @PreAuthorize ou @Publico, e as rotas @Publico são exatamente as de
 * {@link #ROTAS_PUBLICAS}). CORS e Origin pelas origens do perfil; headers de segurança em toda
 * resposta, HSTS só no prod. Sem login ou com token inválido → 401; sem permissão → 403, ambos em
 * Problem Details.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SegurancaConfig {

    /** Única lista de rotas abertas sem login (fora o health do actuator). */
    public static final List<RotaPublica> ROTAS_PUBLICAS = List.of(
            new RotaPublica(HttpMethod.POST, "/api/contas/cliente"),
            new RotaPublica(HttpMethod.POST, "/api/contas/email/confirmar"),
            new RotaPublica(HttpMethod.POST, "/api/contas/posse/celular"),
            new RotaPublica(HttpMethod.POST, "/api/contas/posse/celular/confirmar"),
            new RotaPublica(HttpMethod.POST, "/api/contas/posse/email"),
            new RotaPublica(HttpMethod.POST, "/api/contas/posse/email/confirmar"),
            new RotaPublica(HttpMethod.POST, "/api/auth/entrar"),
            new RotaPublica(HttpMethod.POST, "/api/auth/segundo-passo"),
            new RotaPublica(HttpMethod.POST, "/api/auth/codigo"),
            new RotaPublica(HttpMethod.POST, "/api/auth/entrar-com-codigo"),
            new RotaPublica(HttpMethod.POST, "/api/auth/renovar"),
            new RotaPublica(HttpMethod.POST, "/api/auth/senha/esqueci"),
            new RotaPublica(HttpMethod.POST, "/api/auth/senha/redefinir"));

    private static final int CUSTO_BCRYPT = 12;
    private static final long HSTS_UM_ANO = 31_536_000L;
    private static final long CORS_CACHE_SEGUNDOS = 3_600L;
    /** Rotas públicas não leem o header Authorization (um token vencido não pode barrá-las). */
    private static final Set<String> ROTAS_SEM_BEARER =
            ROTAS_PUBLICAS.stream().map(RotaPublica::caminho).collect(Collectors.toUnmodifiableSet());

    @Bean
    SecurityFilterChain cadeiaDaApi(
            HttpSecurity http,
            JsonMapper json,
            JwtDecoder decodificador,
            OrigensPermitidas origens,
            @Value("${coe.seguranca.hsts:false}") boolean hsts)
            throws Exception {
        AuthenticationEntryPoint naoAutenticado = (requisicao, resposta, erro) -> {
            resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            RespostaDeProblema.escrever(
                    resposta,
                    json,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "nao-autenticado",
                    "Não autenticado",
                    "Entre na sua conta para continuar.");
        };
        AccessDeniedHandler semPermissao = (requisicao, resposta, erro) -> RespostaDeProblema.escrever(
                resposta,
                json,
                HttpServletResponse.SC_FORBIDDEN,
                "proibido",
                "Acesso negado",
                "Você não tem permissão para fazer isso.");
        // API stateless com token no header Authorization: sem cookie de sessão. As duas rotas que
        // usam o cookie do refresh (SameSite=Strict, só /api/auth) exigem Origin da lista (FiltroDeOrigem).
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(fonteCors(origens)))
                .addFilterBefore(new FiltroDeOrigem(origens, json), CorsFilter.class)
                .headers(cabecalhos -> {
                    cabecalhos.referrerPolicy(politica -> politica.policy(ReferrerPolicy.NO_REFERRER));
                    cabecalhos.frameOptions(quadros -> quadros.deny());
                    if (hsts) {
                        cabecalhos.httpStrictTransportSecurity(
                                transporte -> transporte.includeSubDomains(true).maxAgeInSeconds(HSTS_UM_ANO));
                    } else {
                        cabecalhos.httpStrictTransportSecurity(transporte -> transporte.disable());
                    }
                })
                .authorizeHttpRequests(regras -> {
                    regras.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    ROTAS_PUBLICAS.forEach(rota -> regras.requestMatchers(rota.metodo(), rota.caminho())
                            .permitAll());
                    regras.requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll()
                            .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                            .anyRequest()
                            .authenticated();
                })
                .oauth2ResourceServer(recurso -> recurso.bearerTokenResolver(tokenForaDoLogin())
                        .jwt(jwt -> jwt.decoder(decodificador).jwtAuthenticationConverter(papeisDoToken()))
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao))
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(naoAutenticado).accessDeniedHandler(semPermissao));
        return http.build();
    }

    /** CORS só para as origens do perfil; credenciais (cookie) só no /api/auth/**. */
    private static UrlBasedCorsConfigurationSource fonteCors(OrigensPermitidas origens) {
        CorsConfiguration autenticacao = new CorsConfiguration();
        autenticacao.setAllowedOrigins(origens.lista());
        autenticacao.setAllowedMethods(List.of("POST", "OPTIONS"));
        autenticacao.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        autenticacao.setAllowCredentials(true);
        autenticacao.setMaxAge(CORS_CACHE_SEGUNDOS);

        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(origens.lista());
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        api.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        api.setAllowCredentials(false);
        api.setMaxAge(CORS_CACHE_SEGUNDOS);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/auth/**", autenticacao);
        fonte.registerCorsConfiguration("/api/**", api);
        return fonte;
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
}
