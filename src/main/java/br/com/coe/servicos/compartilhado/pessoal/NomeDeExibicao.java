package br.com.coe.servicos.compartilhado.pessoal;

import java.util.Locale;

/**
 * O nome que um usuário vê do outro antes do pagamento (busca, perfil, chat): "Valdir Schmitt" vira "Valdir S."; um
 * nome só fica como está. Nunca o sobrenome inteiro (RN22, PA13).
 */
public final class NomeDeExibicao {

    private NomeDeExibicao() {}

    public static String de(String nome) {
        String[] partes = nome.strip().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        String ultimo = partes[partes.length - 1];
        return partes[0] + " " + ultimo.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }
}
