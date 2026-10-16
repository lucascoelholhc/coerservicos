package br.com.coe.servicos.compartilhado.censura;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Bloqueia contato em texto livre antes do pagamento (RN47): telefone com ou sem separadores, números por
 * extenso, e-mail (inclusive "arroba"), links, @perfil, nomes de redes (zap, whats, insta, telegram) e pedidos
 * como "me liga". CEP passa (PA13): {@code 00000-000} ou 8 dígitos depois de "CEP". Valores em R$, medidas,
 * horas e datas passam. A decisão é sempre do servidor; o front só avisa antes.
 */
public final class CensuraDeContato {

    static final String MENSAGEM =
            "Tire telefone, e-mail, link ou rede social do texto. O contato aparece depois do pagamento.";

    // O que parece número de telefone mas é permitido: some antes de contar os dígitos.
    private static final Pattern CEP = Pattern.compile("\\bcep\\s*:?\\s*\\d{5}-?\\d{3}\\b|\\b\\d{5}-\\d{3}\\b");
    private static final Pattern DINHEIRO = Pattern.compile("r\\$\\s*\\d[\\d.,]*");
    private static final Pattern DATA = Pattern.compile("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b");
    private static final Pattern MEDIDA = Pattern.compile(
            "\\b\\d+(?:[.,]\\d+)*\\s*(?:m2|m²|m³|m|cm|mm|km|kg|g|l|litros?|metros?|horas?|h|anos?|dias?|meses|%)(?![a-z])");

    private static final Pattern TELEFONE = Pattern.compile("\\d(?:[\\s().\\-/+]*\\d){7,}");
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\])\\s*[a-z0-9-]+(?:\\.[a-z0-9-]+)+|\\barroba\\b|\\bponto\\s+com\\b");
    private static final Pattern LINK = Pattern.compile(
            "https?://|www\\.|\\bwa\\.me\\b|\\bt\\.me\\b|\\bbit\\.ly\\b|\\b[a-z0-9-]+\\.(?:com|net|org|br|me|ly|io)\\b");
    private static final Pattern PERFIL = Pattern.compile("(?<![\\w.])@[a-z0-9_.]{3,}");
    private static final Pattern REDE = Pattern.compile(
            "\\b(?:zap|zapzap|whats|whatsapp|wpp|watts|insta|instagram|telegram|facebook|tiktok|kwai)\\b");
    private static final Pattern PEDIDO = Pattern.compile("\\bme\\s+(?:liga|ligue|chama|chame|add|adiciona)\\b"
            + "|\\bme\\s+manda\\s+(?:mensagem|msg)\\b|\\bliga\\s+pra\\s+mim\\b|\\bligue\\s+(?:pra|para)\\s+mim\\b"
            + "|\\bmeu\\s+(?:numero|telefone|celular|contato|whats)\\b");

    private static final Set<String> NUMEROS_POR_EXTENSO = Set.of(
            "zero", "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "meia", "sete", "oito", "nove");
    private static final int DIGITOS_DE_TELEFONE_POR_EXTENSO = 8;

    private CensuraDeContato() {}

    /** Verdadeiro se o texto tem contato; nulo e vazio não têm. */
    public static boolean temContato(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String normalizado = normalizar(texto);
        if (EMAIL.matcher(normalizado).find()
                || LINK.matcher(normalizado).find()
                || PERFIL.matcher(normalizado).find()
                || REDE.matcher(normalizado).find()
                || PEDIDO.matcher(normalizado).find()
                || numerosPorExtenso(normalizado)) {
            return true;
        }
        String semPermitidos = remover(MEDIDA, remover(DATA, remover(DINHEIRO, remover(CEP, normalizado))));
        return TELEFONE.matcher(semPermitidos).find();
    }

    /**
     * Recusa o texto com contato: 422 {@code texto-com-contato}.
     *
     * @param campo nome do campo no pedido (reservado para o front destacar onde está o problema)
     */
    public static void exigirSemContato(String texto, String campo) {
        if (temContato(texto)) {
            throw new RegraDeNegocioException("texto-com-contato", MENSAGEM);
        }
    }

    /** Minúsculas e sem acento: "ZÁP" e "zap" são a mesma coisa. */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String remover(Pattern padrao, String texto) {
        return padrao.matcher(texto).replaceAll(" ");
    }

    /** Oito ou mais números por extenso seguidos ("quatro sete nove nove..."): um telefone ditado. */
    private static boolean numerosPorExtenso(String texto) {
        int seguidos = 0;
        for (String palavra : texto.split("[^a-z]+")) {
            if (NUMEROS_POR_EXTENSO.contains(palavra)) {
                seguidos++;
                if (seguidos >= DIGITOS_DE_TELEFONE_POR_EXTENSO) {
                    return true;
                }
            } else if (!palavra.isEmpty()) {
                seguidos = 0;
            }
        }
        return false;
    }
}
