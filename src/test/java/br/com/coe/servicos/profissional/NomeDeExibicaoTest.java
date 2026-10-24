package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** DOM-04: o nome público é "Primeiro S." (nunca o sobrenome completo) e o "Sobre" igual à prévia do cadastro. */
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

    @Test
    void sobreComPontoEmCadaFrase() {
        assertThat(BuscaPublicaService.textoDoSobre(
                        List.of("Chego no horário combinado", "Levo meus panos."), "  Trabalho no Garcia. "))
                .isEqualTo("Chego no horário combinado. Levo meus panos. Trabalho no Garcia.");
        assertThat(BuscaPublicaService.textoDoSobre(List.of(), "Só o texto.")).isEqualTo("Só o texto.");
        assertThat(BuscaPublicaService.textoDoSobre(List.of("Atendo aos sábados!"), null))
                .isEqualTo("Atendo aos sábados!");
    }
}
