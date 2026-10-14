package br.com.coe.servicos.compartilhado.limite;

import java.util.Objects;

/**
 * Um balde a cobrar: a regra (nome em {@code coe.limites.regras}) e a chave dentro dela. A chave nunca
 * leva dado pessoal em claro: login, celular e e-mail entram como HMAC (quem chama converte). O balde
 * global não tem chave: o limitador sorteia uma das fatias da regra (H1).
 */
public record Balde(String regra, String chave) {

    static final String GLOBAL = "global";

    public Balde {
        Objects.requireNonNull(regra, "regra");
        Objects.requireNonNull(chave, "chave");
        if (chave.isBlank()) {
            throw new IllegalArgumentException("Chave do balde vazia (regra " + regra + ")");
        }
    }

    public static Balde porChave(String regra, String chave) {
        return new Balde(regra, chave);
    }

    public static Balde global(String regra) {
        return new Balde(regra, GLOBAL);
    }

    boolean global() {
        return GLOBAL.equals(chave);
    }
}
