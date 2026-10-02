package br.com.coe.servicos.compartilhado.dinheiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Valor em reais: escala 2, nunca nulo, nunca negativo e no máximo o teto de {@code numeric(12,2)}.
 * Igualdade por valor (280 = 280.00). Contas com percentual arredondam com HALF_EVEN; entrada com
 * mais de 2 casas significativas é recusada (nada é arredondado sem avisar). No JSON é texto: "280.00".
 */
@JsonSerialize(using = DinheiroJson.Serializador.class)
@JsonDeserialize(using = DinheiroJson.Desserializador.class)
public record Dinheiro(BigDecimal valor) implements Comparable<Dinheiro> {

    /** Maior valor que cabe em numeric(12,2). */
    public static final BigDecimal TETO = new BigDecimal("9999999999.99");

    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

    private static final int ESCALA = 2;
    /** Dígitos antes da vírgula que cabem em numeric(12,2). */
    private static final int DIGITOS_INTEIROS = 10;
    /** Tamanho máximo do texto aceito em {@link #de(String)}: barra entrada abusiva antes do parse. */
    private static final int TAMANHO_MAXIMO_TEXTO = 32;

    private static final Locale PT_BR = Locale.of("pt", "BR");

    public Dinheiro {
        Objects.requireNonNull(valor, "valor");
        // Checagens baratas antes de qualquer setScale: um expoente enorme ("1E+999999999")
        // faria o BigDecimal materializar o número e travar a JVM.
        if (valor.signum() < 0) {
            throw new RegraDeNegocioException("valor-negativo", "O valor não pode ser negativo.");
        }
        // Até 10 dígitos inteiros e 2 casas: o maior valor possível é exatamente o TETO.
        if (valor.precision() - valor.scale() > DIGITOS_INTEIROS) {
            throw new RegraDeNegocioException("valor-acima-do-limite", "O valor passa do limite permitido.");
        }
        if (valor.stripTrailingZeros().scale() > ESCALA) {
            throw new IllegalArgumentException("Valor com mais de 2 casas decimais");
        }
        valor = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
    }

    /** Cria a partir do texto decimal com ponto, ex.: "280.00". */
    public static Dinheiro de(String valor) {
        if (valor.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException("Valor em texto longo demais");
        }
        return new Dinheiro(new BigDecimal(valor));
    }

    public Dinheiro somar(Dinheiro outro) {
        return new Dinheiro(valor.add(outro.valor));
    }

    /** Subtrai; resultado negativo é regra de negócio violada. */
    public Dinheiro subtrair(Dinheiro outro) {
        return new Dinheiro(valor.subtract(outro.valor));
    }

    /** Aplica o percentual e arredonda para centavos com HALF_EVEN. */
    public Dinheiro multiplicar(Percentual percentual) {
        return new Dinheiro(valor.multiply(percentual.valor()).setScale(ESCALA, RoundingMode.HALF_EVEN));
    }

    /** Formato pt-BR para mostrar ao usuário, ex.: "R$ 1.234,56" (com espaço comum). */
    public String formatar() {
        return NumberFormat.getCurrencyInstance(PT_BR)
                .format(valor)
                .replace('\u00a0', ' ')
                .replace('\u202f', ' ');
    }

    @Override
    public int compareTo(Dinheiro outro) {
        return valor.compareTo(outro.valor);
    }

    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
