package br.com.coe.servicos.usuario;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalização e checagem dos dados de contato do cadastro (CORE-02). */
public final class Contato {

    /** Como a pessoa escreve o celular: dígitos, +, espaço, parênteses, ponto e hífen; nada de letra. */
    private static final Pattern FORMATO_CELULAR = Pattern.compile("\\+?[\\d\\s().-]{10,25}");
    /** Celular brasileiro com DDD: 11 dígitos, DDD sem zero na frente e o 3º dígito igual a 9. */
    private static final Pattern CELULAR = Pattern.compile("[1-9][0-9]9[0-9]{8}");
    /**
     * Mesmo formato do CHECK de usuario.email (V2), recusando também caractere de controle e espaço
     * especial (NBSP e afins), que o \s do Java não pega e o banco recusaria.
     */
    private static final Pattern EMAIL =
            Pattern.compile("[^@\\s\\p{C}\\p{Z}]+@[^@\\s\\p{C}\\p{Z}]+\\.[^@\\s\\p{C}\\p{Z}]+");

    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final int TAMANHO_MAXIMO_EMAIL = 254;
    private static final int TAMANHO_MAXIMO_CEP = 9;
    private static final int DIGITOS_COM_PAIS = 13;
    private static final String CODIGO_DO_PAIS = "55";

    private Contato() {}

    /**
     * Só os dígitos. O "55" do país só sai quando o número tem 13 dígitos (55 + DDD + 9 dígitos):
     * 55 também é DDD (Santa Maria/RS), então "55 99123-4567" (11 dígitos) fica como está.
     */
    public static String normalizarCelular(String celular) {
        if (celular == null) {
            return null;
        }
        String digitos = celular.replaceAll("\\D", "");
        if (digitos.length() == DIGITOS_COM_PAIS && digitos.startsWith(CODIGO_DO_PAIS)) {
            return digitos.substring(CODIGO_DO_PAIS.length());
        }
        return digitos;
    }

    public static boolean celularValido(String celularNormalizado) {
        return celularNormalizado != null && CELULAR.matcher(celularNormalizado).matches();
    }

    /** O texto digitado parece um celular (sem letras nem símbolos estranhos) e, normalizado, é válido. */
    public static boolean celularDigitadoValido(String celular) {
        return celular != null
                && FORMATO_CELULAR.matcher(celular.strip()).matches()
                && celularValido(normalizarCelular(celular));
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean emailValido(String emailNormalizado) {
        return emailNormalizado != null
                && emailNormalizado.length() <= TAMANHO_MAXIMO_EMAIL
                && EMAIL.matcher(emailNormalizado).matches();
    }

    /** O CEP chega com ou sem hífen; só esses dois formatos são aceitos. */
    public static boolean cepValido(String cep) {
        if (cep == null) {
            return false;
        }
        String semEspacos = cep.strip();
        return semEspacos.length() <= TAMANHO_MAXIMO_CEP
                && CEP.matcher(semEspacos).matches();
    }

    public static String normalizarCep(String cep) {
        return cep == null ? null : cep.replaceAll("\\D", "");
    }

    public static String normalizarNome(String nome) {
        return nome == null ? null : nome.strip();
    }
}
