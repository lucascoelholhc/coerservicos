package br.com.coe.servicos.compartilhado.censura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/** Censura de contato antes do pagamento (RN47, PA13): mínima do DOM-02; o DOM-05 completa. */
class CensuraDeContatoTest {

    @ParameterizedTest(name = "bloqueia: {0}")
    @ValueSource(
            strings = {
                "Me liga: 47 99999-0001",
                "47999990001",
                "(47) 9 9999 0001",
                "+55 47 99999 0001",
                "4799 9990 001",
                "fone 3333-4444",
                "quatro sete nove nove nove nove zero zero zero um",
                "joao.pintor@gmail.com",
                "joao arroba gmail ponto com",
                "meu email é joao(at)gmail.com",
                "www.meusite.com.br",
                "https://wa.me/5547999990001",
                "acesse meusite.com.br",
                "me chama no zap",
                "chama no WhatsApp",
                "segue no insta @joao.pintor",
                "@joaopintor",
                "Telegram: joaopintor",
                "Me liga que eu explico",
                "Liga pra mim depois",
                // revisões do DOM-02: contornos que passavam
                "47999990001 m",
                "47999990001 h",
                "R$ 47999990001",
                "R$47999123456,00",
                "4799999 0001kg",
                "\uFF14\uFF17\uFF19\uFF19\uFF19\uFF19\uFF19\uFF10\uFF10\uFF10\uFF11",
                "\u0664\u0667\u0669\u0669\u0669\u0669\u0669\u0660\u0660\u0660\u0661",
                "4\u200B7\u200B9\u200B9\u200B9\u200B9\u200B9\u200B0\u200B0\u200B0\u200B1",
                "me chama no za\u200Bp",
                "47,99912,3456",
                "4799912_3456",
                "47*99912*3456",
                "47 | 99912 | 3456",
                "nove nove nove nove traço um dois três quatro",
                "47 nove nove nove um dois tres quatro",
                "quarenta e sete nove nove nove nove um dois",
                "meu site loja.app",
                "linktr.ee/joaopintor",
                "joao at gmail",
                "joao arr0ba gmail",
                "me procura no gmail",
                "chama no direct",
                "me manda dm",
                "chama no zaap",
                "manda no wats"
            })
    void bloqueia(String texto) {
        assertThat(CensuraDeContato.temContato(texto)).isTrue();
        assertThatThrownBy(() -> CensuraDeContato.exigirSemContato(texto, "bio"))
                .isInstanceOfSatisfying(RegraDeNegocioException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("texto-com-contato");
                    assertThat(erro.getMessage()).contains("depois do pagamento");
                });
    }

    @ParameterizedTest(name = "passa: {0}")
    @ValueSource(
            strings = {
                "CEP 89010-000",
                "CEP 89010000",
                "cep: 89010-000, perto do centro",
                "Moro no 89010-000 e atendo a região",
                "R$ 280,00 por dia",
                "Cobro R$ 1.250,00 a obra toda",
                "Muro de 12 m e 50 m² de piso",
                "Fiz 1.250 metros de calçada",
                "Trabalho desde 15/03/2010",
                "Atendo das 7h às 17h, de segunda a sábado",
                "Tenho 12 anos de experiência e 3 filhos",
                "Faço 2 diárias por semana no mesmo cliente",
                "Sou caprichoso e pontual. Levo as minhas ferramentas.",
                "Ganhei 10% de desconto no material",
                "Pinto 3 cômodos, 2 banheiros e 1 cozinha",
                "Pintei a face externa do muro",
                "O Sr.João indicou meu trabalho",
                "Obra de 120 m2 entregue em 2019",
                "Cobro R$ 1.250,00 a obra toda, R$ 280 a diária",
                ""
            })
    void passa(String texto) {
        assertThat(CensuraDeContato.temContato(texto)).isFalse();
        assertThatCode(() -> CensuraDeContato.exigirSemContato(texto, "bio")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nulo passa (campo opcional vazio)")
    void nulo() {
        assertThat(CensuraDeContato.temContato(null)).isFalse();
        assertThatCode(() -> CensuraDeContato.exigirSemContato(null, "bio")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acento e maiúscula não escapam: ZAP, Záp")
    void acentos() {
        assertThat(CensuraDeContato.temContato("ME CHAMA NO ZÁP")).isTrue();
        assertThat(CensuraDeContato.temContato("Instagram do trabalho")).isTrue();
    }

    @Test
    @DisplayName("poucos números por extenso não bloqueiam (\"faço dois ou três cômodos\")")
    void poucosNumerosPorExtenso() {
        assertThat(CensuraDeContato.temContato("faço dois ou três cômodos por dia, às vezes quatro"))
                .isFalse();
    }
}
