package br.com.coe.servicos.compartilhado.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** O código da regra vira parte do tipo do erro (urn:coe:erro:<codigo>), então precisa ser bem formado. */
class RegraDeNegocioExceptionTest {

    @ParameterizedTest(name = "aceita o código {0}")
    @ValueSource(strings = {"limite-lc150", "dia-ocupado", "x"})
    void aceitaCodigoBemFormado(String codigo) {
        assertThat(new RegraDeNegocioException(codigo, "mensagem").getCodigo()).isEqualTo(codigo);
    }

    @ParameterizedTest(name = "recusa o código \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"Limite", "com espaço", "-inicio", "fim-", "duplo--hifen", "acentuação"})
    @DisplayName("recusa código fora do padrão kebab-case")
    void recusaCodigoMalFormado(String codigo) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RegraDeNegocioException(codigo, "mensagem"));
    }
}
