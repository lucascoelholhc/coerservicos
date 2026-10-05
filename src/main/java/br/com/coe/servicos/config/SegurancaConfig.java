package br.com.coe.servicos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.security.web.SecurityFilterChain;

import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API (CORE-02, primeira versão): sem sessão, tudo exige login menos o cadastro de
 * cliente e o health. Sem login → 401; sem permissão → 403, ambos em Problem Details. O login com
 * JWT entra no CORE-03; o CSRF da renovação por cookie, no CORE-08.
 */
@Configuration(proxyBeanMethods = false)
public class SegurancaConfig {

    private static final int CUSTO_BCRYPT = 12;
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    SecurityFilterChain cadeiaDaApi(HttpSecurity http, JsonMapper json) throws Exception {
        // API stateless com token no header Authorization: sem cookie de sessão, não há CSRF a
        // proteger. O CSRF volta só no endpoint de renovação por cookie (CORE-08).
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contas/cliente")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(erros -> erros.authenticationEntryPoint((requisicao, resposta, erro) -> {
                            resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            escrever(
                                    resposta,
                                    json,
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "nao-autenticado",
                                    "Não autenticado",
                                    "Entre na sua conta para continuar.");
                        })
                        .accessDeniedHandler((requisicao, resposta, erro) -> escrever(
                                resposta,
                                json,
                                HttpServletResponse.SC_FORBIDDEN,
                                "proibido",
                                "Acesso negado",
                                "Você não tem permissão para fazer isso.")));
        return http.build();
    }

    /**
     * Senhas com {bcrypt} e custo 12. Hash sem prefixo (o "$2b$..." do seed local) é conferido como
     * bcrypt, para os usuários de teste conseguirem entrar.
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
