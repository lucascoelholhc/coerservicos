package br.com.coe.servicos.compartilhado.pessoal;

import java.util.regex.Pattern;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.log.Mascara;

/**
 * CPF validado pelos dígitos verificadores. Guarda só os 11 dígitos; {@link #toString()} é mascarado
 * ({@code ***.***.***-25}) para nunca ir inteiro para log. No banco vai cifrado ({@code CpfCifradoConverter})
 * e como HMAC para unicidade ({@code HmacDeCpf}); nunca comparado decifrado.
 */
public final class Cpf {

    private static final Pattern PERMITIDO = Pattern.compile("[0-9.\\-\\s]+");
    private static final int DIGITOS = 11;

    private final String digitos;

    private Cpf(String digitos) {
        this.digitos = digitos;
    }

    /** Aceita com ou sem pontos, traço e espaços. Inválido = 422 {@code cpf-invalido}. */
    public static Cpf de(String texto) {
        if (texto == null || !PERMITIDO.matcher(texto).matches()) {
            throw invalido();
        }
        String digitos = texto.replaceAll("\\D", "");
        if (digitos.length() != DIGITOS || digitos.chars().allMatch(c -> c == digitos.charAt(0))) {
            throw invalido();
        }
        if (verificador(digitos, 9) != digitos.charAt(9) - '0'
                || verificador(digitos, 10) != digitos.charAt(10) - '0') {
            throw invalido();
        }
        return new Cpf(digitos);
    }

    private static int verificador(String digitos, int quantos) {
        int soma = 0;
        for (int i = 0; i < quantos; i++) {
            soma += (digitos.charAt(i) - '0') * (quantos + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static RegraDeNegocioException invalido() {
        return new RegraDeNegocioException("cpf-invalido", "CPF inválido. Confira os números.");
    }

    public String digitos() {
        return digitos;
    }

    /** {@code 529.982.247-25}: só para mostrar ao próprio dono. */
    public String formatado() {
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-"
                + digitos.substring(9);
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Cpf cpf && digitos.equals(cpf.digitos);
    }

    @Override
    public int hashCode() {
        return digitos.hashCode();
    }

    /** Mascarado: nunca o CPF inteiro em log. */
    @Override
    public String toString() {
        return Mascara.cpf(digitos);
    }
}
