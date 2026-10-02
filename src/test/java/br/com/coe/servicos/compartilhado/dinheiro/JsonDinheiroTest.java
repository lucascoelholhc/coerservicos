package br.com.coe.servicos.compartilhado.dinheiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** No JSON, Dinheiro e Percentual são texto; nunca número de ponto flutuante. */
class JsonDinheiroTest {

    record Item(Dinheiro valor, Percentual comissao) {}

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("Dinheiro sai como \"280.00\" e Percentual como \"0.1000\"")
    void serializaComoTexto() {
        String saida = json.writeValueAsString(new Item(Dinheiro.de("280"), Percentual.de("0.1")));

        assertThat(saida).isEqualTo("{\"valor\":\"280.00\",\"comissao\":\"0.1000\"}");
    }

    @Test
    @DisplayName("ida e volta sem perder centavos")
    void idaEVolta() {
        Item original = new Item(Dinheiro.de("1234.56"), Percentual.de("0.0750"));

        Item lido = json.readValue(json.writeValueAsString(original), Item.class);

        assertThat(lido).isEqualTo(original);
    }

    @Test
    @DisplayName("recusa Dinheiro ou Percentual enviados como número")
    void recusaNumero() {
        assertThatThrownBy(() -> json.readValue("{\"valor\":280.00,\"comissao\":\"0.1\"}", Item.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue("{\"valor\":\"280.00\",\"comissao\":0.1}", Item.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("texto inválido, com 3 casas, negativo ou acima do teto vira erro de leitura do Jackson")
    void valorInvalidoViraErroDeLeitura() {
        for (String valor : new String[] {"abc", "", "280.005", "-1.00", "10000000000.00", "1E+999999999"}) {
            assertThatThrownBy(() -> json.readValue("{\"valor\":\"" + valor + "\",\"comissao\":null}", Item.class))
                    .as(valor)
                    .isInstanceOf(JacksonException.class);
        }
        assertThatThrownBy(() -> json.readValue("{\"valor\":null,\"comissao\":\"1.00\"}", Item.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("aceita nulo no JSON como ausência de valor")
    void aceitaNulo() {
        Item lido = json.readValue("{\"valor\":null,\"comissao\":null}", Item.class);

        assertThat(lido.valor()).isNull();
        assertThat(lido.comissao()).isNull();
    }
}
