package br.com.coe.servicos.compartilhado.mensageria;

/** Celular e e-mail mascarados para log: nunca o dado inteiro (LGPD). */
public final class Mascara {

    private static final int MINIMO_DIGITOS = 8;
    private static final int INICIO_VISIVEL = 2;
    private static final int FIM_VISIVEL = 4;

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
}
