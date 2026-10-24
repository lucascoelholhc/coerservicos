package br.com.coe.servicos.compartilhado.censura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DOM-05: qual regra a mensagem quebrou (RN48: o autor vê o motivo) e o texto bloqueado guardado mascarado. */
class RegrasDaCensuraTest {

    @Test
    @DisplayName("cada tipo de contato vira a sua regra, na ordem fixa")
    void regras() {
        assertThat(CensuraDeContato.regrasVioladas("Me liga no 47 99999-0001")).containsExactly("telefone", "pedido");
        assertThat(CensuraDeContato.regrasVioladas("escreve pra joao.silva@gmail.com"))
                .containsExactly("email");
        assertThat(CensuraDeContato.regrasVioladas("veja www.minhaobra.com.br")).containsExactly("link");
        assertThat(CensuraDeContato.regrasVioladas("me segue lá @joao_pedreiro"))
                .containsExactly("perfil");
        assertThat(CensuraDeContato.regrasVioladas("chama no zap")).containsExactly("rede");
        assertThat(CensuraDeContato.regrasVioladas("quatro sete nove nove nove nove zero zero zero um"))
                .containsExactly("telefone");
    }

    @Test
    @DisplayName("sem contato (CEP, valor, medida, data): nenhuma regra")
    void semContato() {
        assertThat(CensuraDeContato.regrasVioladas("CEP 89010-000, R$ 280,00, 12 m², dia 27/10"))
                .isEmpty();
        assertThat(CensuraDeContato.regrasVioladas(null)).isEmpty();
        assertThat(CensuraDeContato.regrasVioladas("  ")).isEmpty();
        assertThat(CensuraDeContato.temContato("CEP 89010000")).isFalse();
    }

    @Test
    @DisplayName("máscara: números viram •, e-mail, link e @perfil viram [contato]; o resto fica")
    void mascara() {
        assertThat(CensuraDeContato.mascararContato("Me liga 47 99999-0001 ou joao@gmail.com"))
                .isEqualTo("Me liga •• •••••-•••• ou [contato]");
        assertThat(CensuraDeContato.mascararContato("site https://obra.com e @joao_pedreiro"))
                .isEqualTo("site [contato] e [contato]");
        assertThat(CensuraDeContato.mascararContato("tudo certo")).isEqualTo("tudo certo");
    }
}
