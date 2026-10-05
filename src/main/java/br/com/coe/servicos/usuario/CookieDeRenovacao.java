package br.com.coe.servicos.usuario;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh: HttpOnly, SameSite=Strict, só para /api/auth, 30 dias. Secure e domínio por
 * perfil (o local roda em http://localhost, sem Secure).
 */
@Component
class CookieDeRenovacao {

    static final String NOME = "coe_refresh";
    private static final String CAMINHO = "/api/auth";

    private final boolean seguro;
    private final String dominio;

    CookieDeRenovacao(
            @Value("${coe.auth.cookie.secure:true}") boolean seguro,
            @Value("${coe.auth.cookie.dominio:}") String dominio) {
        this.seguro = seguro;
        this.dominio = dominio.isBlank() ? null : dominio;
    }

    /** Max-Age = o tempo que o refresh ainda vale (nunca além do teto da sessão). */
    String criar(RefreshEmitido refresh) {
        return montar(refresh.valor(), refresh.validade());
    }

    String apagar() {
        return montar("", Duration.ZERO);
    }

    private String montar(String valor, Duration validade) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Strict")
                .path(CAMINHO)
                .domain(dominio)
                .maxAge(validade)
                .build()
                .toString();
    }
}
