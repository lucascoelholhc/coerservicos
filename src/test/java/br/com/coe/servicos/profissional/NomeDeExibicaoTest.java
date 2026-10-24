package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** DOM-04: o nome público é "Primeiro S." (nunca o sobrenome completo). */
class NomeDeExibicaoTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(
            delimiter = '|',
            value = {
                "Valdir Schmitt        | Valdir S.",
                "Márcia dos Santos     | Márcia S.",
                "Ana   Paula  souza    | Ana S.",
                "Ivone                 | Ivone",
                "Érico Ávila           | Érico Á."
            })
    void primeiroNomeEInicial(String nome, String esperado) {
        assertThat(BuscaPublicaService.nomeDeExibicao(nome)).isEqualTo(esperado);
    }
}
