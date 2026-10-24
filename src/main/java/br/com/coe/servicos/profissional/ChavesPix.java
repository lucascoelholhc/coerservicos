package br.com.coe.servicos.profissional;

import java.util.Locale;
import java.util.regex.Pattern;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.log.Mascara;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/** Formato da chave Pix pelo tipo (RN15: a COE confere o nome e o CPF antes do primeiro repasse). */
final class ChavesPix {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}");
    private static final Pattern ALEATORIA =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int DIGITOS_DO_CELULAR = 11;

    private ChavesPix() {}

    /** Chave normalizada (só dígitos, minúsculas) ou 422 {@code pix-invalido}. */
    static String normalizar(String tipo, String chave) {
        String limpa = chave.strip();
        return switch (tipo) {
            case "cpf" -> cpf(limpa);
            case "celular" -> celular(limpa);
            case "email" -> exigir(limpa.toLowerCase(Locale.ROOT), EMAIL);
            default -> exigir(limpa.toLowerCase(Locale.ROOT), ALEATORIA);
        };
    }

    private static String cpf(String chave) {
        try {
            return Cpf.de(chave).digitos();
        } catch (RegraDeNegocioException invalido) {
            throw invalida();
        }
    }

    private static String celular(String chave) {
        String digitos = chave.replaceAll("\\D", "");
        if (digitos.length() == DIGITOS_DO_CELULAR + 2 && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() != DIGITOS_DO_CELULAR || digitos.charAt(2) != '9') {
            throw invalida();
        }
        return digitos;
    }

    private static String exigir(String chave, Pattern formato) {
        if (!formato.matcher(chave).matches()) {
            throw invalida();
        }
        return chave;
    }

    private static RegraDeNegocioException invalida() {
        return new RegraDeNegocioException("pix-invalido", "Essa chave Pix não combina com o tipo escolhido. Confira.");
    }

    /** Para mostrar ao próprio dono: nunca a chave inteira. */
    static String mascarar(String tipo, String chave) {
        if (tipo == null || chave == null) {
            return null;
        }
        return switch (tipo) {
            case "cpf" -> Mascara.cpf(chave);
            case "celular" -> Mascara.celular(chave);
            case "email" -> Mascara.email(chave);
            default -> chave.substring(0, 4) + "…";
        };
    }
}
