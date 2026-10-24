package br.com.coe.servicos.compartilhado.censura;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Bloqueia contato em texto livre antes do pagamento (RN47): telefone com qualquer separador, números por
 * extenso (também misturados com dígitos), e-mail (inclusive "arroba" e "at gmail"), links, @perfil, nomes de
 * redes e pedidos como "me liga". CEP passa (PA13): {@code 00000-000} ou 8 dígitos depois de "CEP". Valores em
 * R$, medidas, horas e datas passam, mas só números curtos e nunca colados a outro número. O texto é
 * normalizado antes (largura total, dígitos de outras escritas, caracteres invisíveis, acentos). É barreira para
 * a pessoa comum, não contra má-fé determinada: a decisão é sempre do servidor.
 */
public final class CensuraDeContato {

    static final String MENSAGEM =
            "Tire telefone, e-mail, link ou rede social do texto. O contato aparece depois do pagamento.";

    // O que parece número de telefone mas é permitido: some antes de contar os dígitos.
    private static final Pattern CEP = Pattern.compile("\\bcep\\s*:?\\s*\\d{5}-?\\d{3}\\b|\\b\\d{5}-\\d{3}\\b");
    private static final Pattern DINHEIRO = Pattern.compile("r\\$\\s*\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})?(?!\\d)");
    private static final Pattern DATA = Pattern.compile("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b");
    private static final Pattern MEDIDA =
            Pattern.compile("(?<!\\d[\\s().\\-/+_*|:;,]{0,3})\\b\\d{1,4}(?:[.,]\\d{1,3})?\\s*"
                    + "(?:m2|m3|m|cm|mm|km|kg|g|l|litros?|metros?|horas?|h|anos?|dias?|meses|%)(?![a-z0-9])");

    private static final Pattern TELEFONE = Pattern.compile("\\d(?:[\\s().\\-/+_*|:;,]*\\d){7,}");
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\])\\s*[a-z0-9-]+(?:\\.[a-z0-9-]+)+|\\barr[o0]ba\\b|\\bponto\\s+com\\b"
                    + "|\\b(?:at|em|no)\\s+(?:gmail|hotmail|outlook|yahoo|icloud|live|bol|uol)\\b"
                    + "|\\b(?:gmail|hotmail|outlook|yahoo|icloud)\\b");
    private static final Pattern LINK = Pattern.compile("https?://|www\\.|\\b[a-z0-9-]+\\."
            + "(?:com|net|org|br|me|ly|io|app|dev|xyz|site|online|store|shop|link|ee|am|tv|co|info|biz)\\b");
    private static final Pattern PERFIL = Pattern.compile("(?<![\\w.])@[a-z0-9_.]{3,}");
    // sem "insta" como prefixo: "instalação" é serviço de eletricista; sem "face": "face do muro" é obra
    private static final Pattern REDE = Pattern.compile("\\b(?:zap\\w*|z+a+p+|whats\\w*|wpp|watts?\\w*|wats\\w*|insta"
            + "|instagram|telegram\\w*|facebook|fb|tiktok|kwai|dm|direct|inbox|messenger|signal|linkedin)\\b");
    private static final Pattern PEDIDO = Pattern.compile("\\bme\\s+(?:liga|ligue|chama|chame|add|adiciona)\\b"
            + "|\\bme\\s+manda\\s+(?:mensagem|msg)\\b|\\bliga\\s+pra\\s+mim\\b|\\bligue\\s+(?:pra|para)\\s+mim\\b"
            + "|\\bmeu\\s+(?:numero|telefone|celular|contato|whats)\\b");

    private static final Set<String> UNIDADES = Set.of(
            "zero", "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "meia", "sete", "oito", "nove");
    private static final Set<String> DEZENAS = Set.of(
            "dez",
            "onze",
            "doze",
            "treze",
            "quatorze",
            "catorze",
            "quinze",
            "dezesseis",
            "dezessete",
            "dezoito",
            "dezenove",
            "vinte",
            "trinta",
            "quarenta",
            "cinquenta",
            "sessenta",
            "setenta",
            "oitenta",
            "noventa");
    /** Palavras que alguém põe entre os números ditados ("quatro sete traço nove..."): não quebram a contagem. */
    private static final Set<String> CONECTORES =
            Set.of("e", "traco", "tracinho", "hifen", "ponto", "virgula", "barra");

    private static final int DIGITOS_DE_TELEFONE = 8;

    /** Para a máscara (no texto original): e-mail, link e @perfil inteiros. */
    private static final Pattern CONTATO_ESCRITO = Pattern.compile(
            "[\\w.%+-]+@[\\w-]+(?:\\.[\\w-]+)+|https?://\\S+|www\\.\\S+|(?<![\\w.])@[\\w.]{3,}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern NUMERO = Pattern.compile("\\p{Nd}");

    private CensuraDeContato() {}

    /** Verdadeiro se o texto tem contato; nulo e vazio não têm. */
    public static boolean temContato(String texto) {
        return !regrasVioladas(texto).isEmpty();
    }

    /**
     * As regras que o texto quebra, na ordem fixa telefone, email, link, perfil, rede, pedido (vão para
     * {@code mensagem.regras_violadas} e para o motivo que o autor vê, RN48). Vazio = sem contato.
     */
    public static List<String> regrasVioladas(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String normalizado = normalizar(texto);
        List<String> regras = new ArrayList<>();
        String semPermitidos = remover(MEDIDA, remover(DATA, remover(DINHEIRO, remover(CEP, normalizado))));
        if (TELEFONE.matcher(semPermitidos).find() || numerosDitados(semPermitidos)) {
            regras.add("telefone");
        }
        if (EMAIL.matcher(normalizado).find()) {
            regras.add("email");
        }
        // "joao@gmail.com" é um e-mail, não também um link
        if (LINK.matcher(remover(EMAIL, normalizado)).find()) {
            regras.add("link");
        }
        if (PERFIL.matcher(normalizado).find()) {
            regras.add("perfil");
        }
        if (REDE.matcher(normalizado).find()) {
            regras.add("rede");
        }
        if (PEDIDO.matcher(normalizado).find()) {
            regras.add("pedido");
        }
        return List.copyOf(regras);
    }

    /**
     * O texto como ele pode ser guardado e mostrado à moderação: e-mail, link e @perfil viram "[contato]" e todo
     * número vira "•" (LGPD: a mensagem bloqueada nunca guarda o telefone inteiro).
     */
    public static String mascararContato(String texto) {
        String semEnderecos = CONTATO_ESCRITO.matcher(texto).replaceAll("[contato]");
        return NUMERO.matcher(semEnderecos).replaceAll("•");
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

    /**
     * Minúsculas, sem acento, sem caractere invisível (espaço de largura zero, hífen suave) e com qualquer dígito
     * Unicode virando o ASCII: dígitos de largura total ou arábicos contam como "47"; "ZÁP" é "zap".
     */
    private static String normalizar(String texto) {
        String compativel = Normalizer.normalize(texto, Normalizer.Form.NFKC);
        StringBuilder limpo = new StringBuilder(compativel.length());
        compativel.codePoints().forEach(caractere -> {
            if (Character.getType(caractere) == Character.FORMAT) {
                return;
            }
            if (Character.isDigit(caractere)) {
                limpo.append((char) ('0' + Character.digit(caractere, 10)));
            } else {
                limpo.appendCodePoint(caractere);
            }
        });
        return Normalizer.normalize(limpo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String remover(Pattern padrao, String texto) {
        return padrao.matcher(texto).replaceAll(" ");
    }

    /**
     * Oito ou mais números seguidos, contando dígitos e números por extenso juntos ("47 nove nove…", "quarenta e
     * sete…"): um telefone ditado. Conectores não quebram a sequência; qualquer outra palavra quebra.
     */
    private static boolean numerosDitados(String texto) {
        int seguidos = 0;
        for (String palavra : texto.split("[^a-z0-9]+")) {
            if (palavra.isEmpty() || CONECTORES.contains(palavra)) {
                continue;
            }
            if (palavra.chars().allMatch(Character::isDigit)) {
                seguidos += palavra.length();
            } else if (UNIDADES.contains(palavra)) {
                seguidos++;
            } else if (DEZENAS.contains(palavra)) {
                seguidos += 2;
            } else {
                seguidos = 0;
            }
            if (seguidos >= DIGITOS_DE_TELEFONE) {
                return true;
            }
        }
        return false;
    }
}
