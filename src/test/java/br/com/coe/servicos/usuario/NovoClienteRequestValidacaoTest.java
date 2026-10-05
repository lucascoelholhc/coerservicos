package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras de cada campo do cadastro, com as mensagens em português que vão para a tela. */
class NovoClienteRequestValidacaoTest {

    private static Validator validador;

    @BeforeAll
    static void criarValidador() {
        Locale.setDefault(Locale.of("pt", "BR"));
        validador = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static NovoClienteRequest valido() {
        return new NovoClienteRequest(
                "Ana Silva", "(47) 90000-0101", "ana@teste.coe.local", "89010-000", "Casa-Azul-2026", "1.0");
    }

    private static Set<String> camposComErro(NovoClienteRequest pedido) {
        return validador.validate(pedido).stream()
                .map(violacao -> violacao.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static String mensagem(NovoClienteRequest pedido, String campo) {
        return validador.validate(pedido).stream()
                .filter(violacao -> violacao.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("pedido completo e válido passa")
    void validoPassa() {
        assertThat(validador.validate(valido())).isEmpty();
    }

    @Test
    @DisplayName("todos os campos são obrigatórios")
    void obrigatorios() {
        NovoClienteRequest vazio = new NovoClienteRequest(null, null, null, null, null, null);

        assertThat(camposComErro(vazio))
                .containsExactlyInAnyOrder("nome", "celular", "email", "cep", "senha", "versaoTermosAceita");
    }

    @Test
    @DisplayName("nome com menos de 2 ou mais de 120 caracteres (sem contar espaços das pontas)")
    void nome() {
        NovoClienteRequest curto = new NovoClienteRequest(
                "  A ", "47900000101", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");
        NovoClienteRequest longo = new NovoClienteRequest(
                "A".repeat(121), "47900000101", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(curto)).containsExactly("nome");
        assertThat(camposComErro(longo)).containsExactly("nome");
    }

    @Test
    @DisplayName("celular inválido: \"Escreva seu celular com DDD\"")
    void celular() {
        NovoClienteRequest pedido = new NovoClienteRequest(
                "Ana Silva", "4790000010", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(mensagem(pedido, "celular")).isEqualTo("Escreva seu celular com DDD");
    }

    @Test
    @DisplayName("e-mail com formato inválido")
    void email() {
        NovoClienteRequest pedido =
                new NovoClienteRequest("Ana Silva", "47900000101", "ana@", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("email");
    }

    @ParameterizedTest(name = "CEP inválido: {0}")
    @ValueSource(strings = {"8901000", "890100000", "89010-00a"})
    void cep(String cep) {
        NovoClienteRequest pedido =
                new NovoClienteRequest("Ana Silva", "47900000101", "ana@teste.coe.local", cep, "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("cep");
    }

    @ParameterizedTest(name = "senha recusada: {0}")
    @ValueSource(strings = {"curta12", "12345678", "SENHA123", "senha1234", "qwerty123"})
    void senhaCurtaOuObvia(String senha) {
        NovoClienteRequest pedido =
                new NovoClienteRequest("Ana Silva", "47900000101", "ana@teste.coe.local", "89010000", senha, "1.0");

        assertThat(camposComErro(pedido)).containsExactly("senha");
    }

    @Test
    @DisplayName("senha igual ao celular (com ou sem máscara) ou ao e-mail é recusada")
    void senhaIgualAoContato() {
        NovoClienteRequest igualCelular = new NovoClienteRequest(
                "Ana Silva", "(47) 90000-0101", "ana@teste.coe.local", "89010000", "47900000101", "1.0");
        NovoClienteRequest igualEmail = new NovoClienteRequest(
                "Ana Silva", "47900000101", "Ana@Teste.coe.local", "89010000", "ana@teste.coe.local", "1.0");

        assertThat(camposComErro(igualCelular)).containsExactly("senha");
        assertThat(camposComErro(igualEmail)).containsExactly("senha");
    }

    @Test
    @DisplayName("limite de 72 é em bytes (limite do BCrypt): acento conta 2")
    void senhaAte72Bytes() {
        String setentaEDoisBytes = "a".repeat(72);
        String setentaETresBytes = "é" + "a".repeat(71); // é ocupa 2 bytes em UTF-8

        assertThat(camposComErro(new NovoClienteRequest(
                        "Ana Silva", "47900000101", "ana@teste.coe.local", "89010000", setentaEDoisBytes, "1.0")))
                .isEmpty();
        assertThat(camposComErro(new NovoClienteRequest(
                        "Ana Silva", "47900000101", "ana@teste.coe.local", "89010000", setentaETresBytes, "1.0")))
                .containsExactly("senha");
    }

    @ParameterizedTest(name = "nome recusado: [{0}]")
    @ValueSource(strings = {"Ana\u0000Silva", "Ana\u200bSilva", "Ana\tSilva", "12", "--"})
    @DisplayName("nome com caractere de controle, invisível ou sem nenhuma letra é recusado")
    void nomeComCaractereInvalido(String nome) {
        NovoClienteRequest pedido =
                new NovoClienteRequest(nome, "47900000101", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("nome");
    }

    @ParameterizedTest(name = "nome aceito: [{0}]")
    @ValueSource(strings = {"José D'Ávila", "Ana-Maria Souza", "Zé"})
    void nomeComAcentoApostrofoEHifen(String nome) {
        NovoClienteRequest pedido =
                new NovoClienteRequest(nome, "47900000101", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(validador.validate(pedido)).isEmpty();
    }

    @ParameterizedTest(name = "e-mail recusado: [{0}]")
    @ValueSource(strings = {"ana\u0000@teste.coe.local", "ana\u00a0x@teste.coe.local", "ana\u2003x@teste.coe.local"})
    @DisplayName("e-mail com caractere de controle ou espaço especial é recusado (o banco também recusaria)")
    void emailComCaractereInvalido(String email) {
        NovoClienteRequest pedido =
                new NovoClienteRequest("Ana Silva", "47900000101", email, "89010000", "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("email");
    }

    @ParameterizedTest(name = "celular recusado: [{0}]")
    @ValueSource(
            strings = {"47 abc 90000-0101", "47900000101 ramal", "47#900000101", "4 7 9 0 0 0 0 0 1 0 1 0 0 0 0 0 0"})
    @DisplayName("celular com letra, símbolo estranho ou comprido demais é recusado, mesmo com 11 dígitos")
    void celularComLixo(String celular) {
        NovoClienteRequest pedido = new NovoClienteRequest(
                "Ana Silva", celular, "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("celular");
    }

    @Test
    @DisplayName("celular com +55, parênteses, espaço e hífen é aceito")
    void celularComMascaraCompleta() {
        NovoClienteRequest pedido = new NovoClienteRequest(
                "Ana Silva", "+55 (47) 90000-0101", "ana@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0");

        assertThat(validador.validate(pedido)).isEmpty();
    }

    @Test
    @DisplayName("senha igual ao celular escrito com máscara é recusada")
    void senhaIgualAoCelularComMascara() {
        NovoClienteRequest pedido = new NovoClienteRequest(
                "Ana Silva", "47900000101", "ana@teste.coe.local", "89010000", "(47) 90000-0101", "1.0");

        assertThat(camposComErro(pedido)).containsExactly("senha");
    }

    @Test
    @DisplayName("mensagem do limite da senha explica que acento conta mais")
    void mensagemDoLimiteDaSenha() {
        NovoClienteRequest pedido = new NovoClienteRequest(
                "Ana Silva", "47900000101", "ana@teste.coe.local", "89010000", "é".repeat(40), "1.0");

        assertThat(mensagem(pedido, "senha"))
                .isEqualTo("A senha precisa ter de 8 a 72 caracteres (letra com acento conta como 2)");
    }

    @Test
    @DisplayName("campos com tamanho máximo: texto gigante é recusado sem processar")
    void tamanhoMaximo() {
        String gigante = "4".repeat(10_000);
        NovoClienteRequest pedido =
                new NovoClienteRequest("Ana Silva", gigante, "a".repeat(250) + "@x.co", gigante, gigante, "1.0");

        assertThat(camposComErro(pedido)).containsExactlyInAnyOrder("celular", "email", "cep", "senha");
    }
}
