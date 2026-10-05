package br.com.coe.servicos.usuario.validacao;

import java.util.regex.Pattern;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import br.com.coe.servicos.usuario.Contato;

/**
 * Nome de pessoa: letras (com acento), espaço, apóstrofo, ponto e hífen, com pelo menos uma letra.
 * Caractere de controle ou invisível nunca passa (o banco recusa NUL e a censura não enxerga o resto).
 */
public class NomeDePessoaValidador implements ConstraintValidator<NomeDePessoa, String> {

    private static final int MINIMO = 2;
    private static final int MAXIMO = 120;
    private static final Pattern PERMITIDOS = Pattern.compile("[\\p{L}\\p{M} '\u2019.-]+");
    private static final Pattern TEM_LETRA = Pattern.compile(".*\\p{L}.*");

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null) {
            return true;
        }
        String nome = Contato.normalizarNome(valor);
        return nome.length() >= MINIMO
                && nome.length() <= MAXIMO
                && PERMITIDOS.matcher(nome).matches()
                && TEM_LETRA.matcher(nome).matches();
    }
}
