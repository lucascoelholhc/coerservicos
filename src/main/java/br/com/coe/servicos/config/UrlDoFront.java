package br.com.coe.servicos.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Base dos links mandados por e-mail ({@code coe.front.url-base}). O token vai sempre no
 * fragmento ({@code #token=...}): o navegador não o manda ao servidor, então não aparece em log de
 * acesso nem no Referer. Sem base boa (ou sem https em prod), a aplicação não sobe.
 */
public final class UrlDoFront {

    private final String base;

    private UrlDoFront(String base) {
        this.base = base;
    }

    public static UrlDoFront de(String base, boolean somenteHttps) {
        if (base == null || base.isBlank()) {
            throw invalida("obrigatória (COE_FRONT_URL_BASE)");
        }
        String limpa = base.strip();
        URI uri;
        try {
            uri = new URI(limpa);
        } catch (URISyntaxException erro) {
            throw invalida("não é uma URL");
        }
        String esquema = uri.getScheme();
        if (!"https".equals(esquema) && !"http".equals(esquema)) {
            throw invalida("precisa começar com https:// (ou http:// no local)");
        }
        if (uri.getHost() == null
                || !(uri.getRawPath() == null || uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalida("só esquema, domínio e porta, sem caminho nem barra no fim");
        }
        if (somenteHttps && !"https".equals(esquema)) {
            throw invalida("precisa ser https em produção");
        }
        return new UrlDoFront(limpa);
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("coe.front.url-base inválida: " + motivo);
    }

    /** Ex.: {@code link("/confirmar-email", token)} = base + /confirmar-email#token=... */
    public String link(String caminho, String token) {
        return base + caminho + "#token=" + token;
    }
}
