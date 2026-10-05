package br.com.coe.servicos.usuario.validacao;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import br.com.coe.servicos.usuario.Contato;
import br.com.coe.servicos.usuario.NovoClienteRequest;

/**
 * Senha do cadastro: de 8 a 72 bytes (o BCrypt ignora o que passa de 72), diferente do celular e do
 * e-mail e fora da lista de senhas óbvias. O erro sai no campo "senha".
 */
public class SenhaPermitidaValidador implements ConstraintValidator<SenhaPermitida, NovoClienteRequest> {

    private static final int MINIMO_BYTES = 8;
    private static final int MAXIMO_BYTES = 72;
    private static final Set<String> OBVIAS = Set.of(
            "12345678",
            "123456789",
            "1234567890",
            "87654321",
            "11111111",
            "00000000",
            "abcdefgh",
            "abc12345",
            "senha123",
            "senha1234",
            "password",
            "qwerty123");

    @Override
    public boolean isValid(NovoClienteRequest pedido, ConstraintValidatorContext contexto) {
        String senha = pedido.senha();
        if (senha == null) {
            return true;
        }
        // Mais caracteres que bytes possíveis: recusa antes de converter texto gigante.
        if (senha.length() > MAXIMO_BYTES
                || tamanhoEmBytes(senha) < MINIMO_BYTES
                || tamanhoEmBytes(senha) > MAXIMO_BYTES) {
            return recusar(contexto, "A senha precisa ter de 8 a 72 caracteres (letra com acento conta como 2)");
        }
        String comparavel = senha.strip().toLowerCase(Locale.ROOT);
        if (comparavel.equals(Contato.normalizarCelular(pedido.celular()))
                || igualAoCelularComMascara(senha, pedido.celular())
                || comparavel.equals(Contato.normalizarEmail(pedido.email()))) {
            return recusar(contexto, "Escolha uma senha diferente do seu celular e do seu e-mail");
        }
        if (OBVIAS.contains(comparavel)) {
            return recusar(contexto, "Essa senha é fácil de adivinhar. Escolha outra");
        }
        return true;
    }

    private static int tamanhoEmBytes(String senha) {
        return senha.getBytes(StandardCharsets.UTF_8).length;
    }

    /** "(47) 90000-0101" como senha de quem tem o celular 47900000101. */
    private static boolean igualAoCelularComMascara(String senha, String celular) {
        return Contato.celularDigitadoValido(senha)
                && Contato.normalizarCelular(senha).equals(Contato.normalizarCelular(celular));
    }

    private static boolean recusar(ConstraintValidatorContext contexto, String mensagem) {
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode("senha")
                .addConstraintViolation();
        return false;
    }
}
