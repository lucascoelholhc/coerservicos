package br.com.coe.servicos.compartilhado.pessoal;

import java.util.concurrent.atomic.AtomicLong;

/** CPFs válidos e únicos para testes (o banco não é limpo entre testes; o HMAC do CPF é único). */
public final class CpfsDeTeste {

    // base aleatória por execução: não colide com CPF de outra execução no mesmo banco
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(100_000_000L + Math.floorMod(System.nanoTime(), 800_000_000L));

    private CpfsDeTeste() {}

    public static Cpf unico() {
        while (true) {
            String base = String.format("%09d", SEQUENCIA.incrementAndGet() % 1_000_000_000L);
            int primeiro = digito(base, 10);
            String cpf = base + primeiro + digito(base + primeiro, 11);
            if (!cpf.chars().allMatch(c -> c == cpf.charAt(0))) {
                return Cpf.de(cpf);
            }
        }
    }

    private static int digito(String parcial, int peso) {
        int soma = 0;
        for (int i = 0; i < parcial.length(); i++) {
            soma += (parcial.charAt(i) - '0') * (peso - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
