package br.com.coe.servicos.config;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Origem extra do img-src da CSP: o bucket das fotos do portfólio, servidas por URL assinada (FE-03). Vem por
 * perfil em {@code coe.seguranca.origem-das-imagens}: local e test com o SeaweedFS ({@code http://localhost:8333});
 * prod vazio até a AWS-04/05 (só o host https do bucket). Inválida = a aplicação não sobe.
 */
final class OrigemDasImagens {

    /** Só esquema + host + porta: nada de caminho, curinga, aspas ou ";" que mudem a política. */
    private static final Pattern ORIGEM = Pattern.compile("^https?://[a-z0-9.-]+(:\\d{1,5})?$");

    private OrigemDasImagens() {}

    static String validar(String texto, boolean somenteHttps) {
        String origem = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        if (origem.isEmpty()) {
            return "";
        }
        if (!ORIGEM.matcher(origem).matches()) {
            throw invalida("precisa ser só uma origem, como https://bucket.s3.regiao.amazonaws.com");
        }
        if (somenteHttps) {
            String host = URI.create(origem).getHost();
            if (!origem.startsWith("https://") || "localhost".equals(host) || host.startsWith("127.")) {
                throw invalida("em produção só https e nunca localhost");
            }
        }
        return origem;
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("coe.seguranca.origem-das-imagens inválida: " + motivo + ".");
    }
}
