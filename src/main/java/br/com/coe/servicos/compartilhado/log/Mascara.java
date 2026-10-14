package br.com.coe.servicos.compartilhado.log;

/**
 * Dado pessoal ou segredo mascarado para log (LGPD): nunca o dado inteiro. Use ao montar a mensagem; o
 * conversor do Logback ({@link MascaraDeLog}) é só a rede de segurança para o que escapar.
 */
public final class Mascara {

    private static final int MINIMO_DIGITOS = 8;
    private static final int INICIO_VISIVEL = 2;
    private static final int FIM_VISIVEL = 4;
    private static final int DIGITOS_DO_CPF = 11;
    private static final int DIGITOS_DO_CEP = 8;
    private static final int INICIO_DO_TOKEN = 4;

    private Mascara() {}

    /** {@code 47900000101} vira {@code 47*****0101}. */
    public static String celular(String celular) {
        if (celular == null) {
            return "(sem celular)";
        }
        if (celular.length() < MINIMO_DIGITOS) {
            return "***";
        }
        int escondidos = celular.length() - INICIO_VISIVEL - FIM_VISIVEL;
        return celular.substring(0, INICIO_VISIVEL)
                + "*".repeat(escondidos)
                + celular.substring(celular.length() - FIM_VISIVEL);
    }

    /** {@code ana.silva@exemplo.com} vira {@code a***@exemplo.com}. */
    public static String email(String email) {
        if (email == null) {
            return "(sem e-mail)";
        }
        int arroba = email.indexOf('@');
        if (arroba < 0) {
            return "***";
        }
        String inicio = arroba > 0 ? email.substring(0, 1) : "";
        return inicio + "***" + email.substring(arroba);
    }

    /** {@code 123.456.789-09} (ou só dígitos) vira {@code ***.***.***-09}: só os dígitos verificadores. */
    public static String cpf(String cpf) {
        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (digitos.length() != DIGITOS_DO_CPF) {
            return "***";
        }
        return "***.***.***-" + digitos.substring(DIGITOS_DO_CPF - 2);
    }

    /** {@code 89010-000} vira {@code 890**-***}: só a região (os 3 primeiros dígitos). */
    public static String cep(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != DIGITOS_DO_CEP) {
            return "***";
        }
        return digitos.substring(0, 3) + "**-***";
    }

    /** Token, link ou chave: só os 4 primeiros caracteres, para achar no log sem revelar o segredo. */
    public static String token(String token) {
        if (token == null || token.length() <= INICIO_DO_TOKEN * 2) {
            return "***";
        }
        return token.substring(0, INICIO_DO_TOKEN) + "***";
    }

    /** Código por SMS: nunca aparece, nem em parte. */
    public static String codigo(String codigo) {
        return "******";
    }
}
