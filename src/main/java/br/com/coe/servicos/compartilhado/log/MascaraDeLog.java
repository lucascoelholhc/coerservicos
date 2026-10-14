package br.com.coe.servicos.compartilhado.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rede de segurança dos logs (CORE-11): mascara o texto final de cada linha (e a mensagem da exceção e
 * das causas), para o que escapar do {@link Mascara} na hora de montar a mensagem. Pega e-mail, token
 * (≥ 32 caracteres base64url com letra e dígito, que não seja UUID), CPF, celular, 10 a 11 dígitos
 * soltos (celular ou CPF) e código de 6 dígitos logo depois da palavra "código". Não mexe em UUID,
 * valor em R$, data, epoch em ms, CEP nem nome de classe. Nas pilhas, as linhas "at ..." ficam intactas.
 */
public final class MascaraDeLog {

    private static final Pattern EMAIL =
            Pattern.compile("(?<![\\w.%+-])([\\w%+-])[\\w.%+-]*@([\\w-]+(?:\\.[\\w-]+)*\\.[A-Za-z]{2,})");
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])");
    private static final Pattern UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LETRA = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGITO = Pattern.compile("\\d");
    private static final Pattern CPF = Pattern.compile("(?<![\\d.])\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?![\\d-])");
    private static final Pattern CELULAR_FORMATADO =
            Pattern.compile("(?<![\\w-])(?:\\+?55\\s?)?\\(?(\\d{2})\\)?\\s?(9\\d{4})[-\\s]?(\\d{4})(?![\\w-])");
    private static final Pattern DIGITOS_SOLTOS = Pattern.compile("(?<![\\w.,/:*-])\\d{10,11}(?![\\w.,/:*-])");
    private static final Pattern CODIGO = Pattern.compile("(?iu)(c[oó]digo\\b[^\\d\\n]{0,15})(\\d{6})(?!\\d)");
    private static final Pattern LINHA_DA_PILHA = Pattern.compile("^\\s*(at |\\.\\.\\. \\d+ )");

    private MascaraDeLog() {}

    /** Texto de uma linha de log (ou mensagem de exceção) com o dado pessoal e os segredos mascarados. */
    public static String mascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String resultado = EMAIL.matcher(texto).replaceAll("$1***@$2");
        resultado = substituir(TOKEN, resultado, MascaraDeLog::token);
        resultado = substituir(CPF, resultado, cpf -> Mascara.cpf(cpf.group()));
        resultado = substituir(
                CELULAR_FORMATADO, resultado, cel -> Mascara.celular(cel.group(1) + cel.group(2) + cel.group(3)));
        resultado = substituir(
                DIGITOS_SOLTOS, resultado, digitos -> "*".repeat(digitos.group().length()));
        return CODIGO.matcher(resultado).replaceAll("$1******");
    }

    /** Pilha de exceção: mascara as mensagens (da exceção e das causas) e deixa as linhas "at" intactas. */
    public static String mascararPilha(String pilha) {
        if (pilha == null || pilha.isEmpty()) {
            return pilha;
        }
        StringBuilder resultado = new StringBuilder(pilha.length());
        int inicio = 0;
        while (inicio < pilha.length()) {
            int fim = pilha.indexOf('\n', inicio);
            int corte = fim < 0 ? pilha.length() : fim + 1;
            String linha = pilha.substring(inicio, corte);
            resultado.append(LINHA_DA_PILHA.matcher(linha).find() ? linha : mascarar(linha));
            inicio = corte;
        }
        return resultado.toString();
    }

    private static String token(Matcher candidato) {
        String texto = candidato.group();
        boolean segredo = LETRA.matcher(texto).find()
                && DIGITO.matcher(texto).find()
                && !UUID.matcher(texto).matches();
        return segredo ? Mascara.token(texto) : texto;
    }

    private static String substituir(
            Pattern padrao, String texto, java.util.function.Function<Matcher, String> substituto) {
        Matcher achou = padrao.matcher(texto);
        StringBuilder resultado = new StringBuilder(texto.length());
        while (achou.find()) {
            achou.appendReplacement(resultado, Matcher.quoteReplacement(substituto.apply(achou)));
        }
        achou.appendTail(resultado);
        return resultado.toString();
    }
}
