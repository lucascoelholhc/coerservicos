package br.com.coe.servicos.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Origens do front aceitas no CORS e na checagem de Origin (CORE-08). Sem curinga, sem caminho e
 * sem barra no fim; com {@code somenteHttps} (prod), só https. Configuração ruim impede a subida.
 */
public final class OrigensPermitidas {

    private static final Pattern ORIGEM = Pattern.compile("(https?)://[A-Za-z0-9.-]+(:\\d{1,5})?");

    private final List<String> origens;

    private OrigensPermitidas(List<String> origens) {
        this.origens = List.copyOf(origens);
    }

    /** Lê a lista separada por vírgula (ex.: {@code https://coe.com.br,https://www.coe.com.br}). */
    public static OrigensPermitidas de(String texto, boolean somenteHttps) {
        List<String> origens = new ArrayList<>();
        for (String item : (texto == null ? "" : texto).split(",")) {
            String origem = item.strip();
            if (origem.isEmpty()) {
                continue;
            }
            if (!ORIGEM.matcher(origem).matches()) {
                throw invalida("cada origem é esquema://host[:porta], sem curinga, caminho nem barra no fim");
            }
            if (!origem.equals(origem.toLowerCase(Locale.ROOT)) || !portaValida(origem)) {
                throw invalida("origem em minúsculas e porta entre 1 e 65535 (o navegador manda assim)");
            }
            if (somenteHttps && !origem.startsWith("https://")) {
                throw invalida("em produção só https");
            }
            origens.add(origem);
        }
        if (origens.isEmpty()) {
            throw invalida("lista vazia (COE_ORIGENS_PERMITIDAS)");
        }
        return new OrigensPermitidas(origens);
    }

    private static boolean portaValida(String origem) {
        int doisPontos = origem.lastIndexOf(':');
        if (doisPontos <= origem.indexOf("://")) {
            return true;
        }
        int porta = Integer.parseInt(origem.substring(doisPontos + 1));
        return porta >= 1 && porta <= 65_535;
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Origens permitidas inválidas: " + motivo);
    }

    public boolean permite(String origem) {
        return origem != null && origens.contains(origem);
    }

    public List<String> lista() {
        return origens;
    }
}
