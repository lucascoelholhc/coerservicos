package br.com.coe.servicos.compartilhado.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CORE-11: a rede de segurança dos logs. O que é dado pessoal ou segredo sai mascarado; o que parece mas
 * não é (UUID, valor em R$, data, nome de classe, CEP com prefixo) fica como está.
 */
class MascaraDeLogTest {

    @Nested
    @DisplayName("mascara")
    class Mascara {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                e-mail                       | enviado para ana.silva@exemplo.com.br agora        | ana.silva@exemplo.com.br
                celular só dígitos            | celular 47900000101 sem conta                       | 47900000101
                celular com máscara           | celular (47) 90000-0101 sem conta                   | 90000-0101
                celular com +55               | celular +55 47 900000101 sem conta                  | 900000101
                CPF formatado                 | cpf 123.456.789-09 recusado                         | 123.456.789-09
                CPF só dígitos                | cpf 12345678909 recusado                            | 12345678909
                código em contexto            | COE: seu código é 483920. Vale por 5 minutos.       | 483920
                código sem acento             | codigo=483920                                       | 483920
                token base64url               | link #token=Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg  | Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg
                JWT                           | Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFuYSJ9.abc123def456ghi789jkl012mno345pq | eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFuYSJ9
                """)
        void esconde(String caso, String texto, String segredo) {
            assertThat(MascaraDeLog.mascarar(texto)).as(caso).doesNotContain(segredo);
        }

        @Test
        @DisplayName("e-mail mantém a primeira letra e o domínio; celular formatado mantém DDD e final")
        void formatoDoMascarado() {
            assertThat(MascaraDeLog.mascarar("para ana.silva@exemplo.com")).isEqualTo("para a***@exemplo.com");
            assertThat(MascaraDeLog.mascarar("celular (47) 90000-0101")).isEqualTo("celular 47*****0101");
            assertThat(MascaraDeLog.mascarar("COE: seu código é 483920.")).isEqualTo("COE: seu código é ******.");
        }
    }

    @Nested
    @DisplayName("não mascara")
    class NaoMascara {

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "usuario 3f2b8c1e-9a4d-4c2b-8e1f-0a1b2c3d4e5f entrou",
                    "total R$ 1.234,56 e R$ 280,00",
                    "em 2026-10-14T19:40:32.593-03:00 e 14/10/2026",
                    "epoch em ms 1760480432593",
                    "CEP 89010-000 e CEP 89010000",
                    "6 tentativas, pedido 483920 sem a palavra",
                    "AbstractAuthenticationProcessingFilter falhou",
                    "bean requestMappingHandlerMapping criado",
                    "org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter",
                    "chave limite_balde entrar-login-ip:203.0.113.7",
                    "47*****0101 e a***@exemplo.com já mascarados"
                })
        void mantem(String texto) {
            assertThat(MascaraDeLog.mascarar(texto)).isEqualTo(texto);
        }

        @Test
        @DisplayName("texto nulo ou vazio volta como veio")
        void vazio() {
            assertThat(MascaraDeLog.mascarar(null)).isNull();
            assertThat(MascaraDeLog.mascarar("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("pilha de exceção")
    class Pilha {

        private static final String PILHA = """
                java.lang.IllegalStateException: falhou para ana@exemplo.com e 47900000101
                \tat br.com.coe.servicos.usuario.ServicoDeLogin.entrar(ServicoDeLogin.java:61)
                \tat org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.attempt(UsernamePasswordAuthenticationFilter.java:85)
                \t... 12 common frames omitted
                Caused by: java.lang.RuntimeException: token Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg
                \tat br.com.coe.servicos.Algo1234567890Abcdefghijklmnopqrstu.metodo(Algo.java:10)
                """;

        @Test
        @DisplayName("mascara a mensagem da exceção e das causas")
        void mensagens() {
            String mascarada = MascaraDeLog.mascararPilha(PILHA);

            assertThat(mascarada)
                    .doesNotContain("ana@exemplo.com")
                    .doesNotContain("47900000101")
                    .doesNotContain("Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg")
                    .contains("java.lang.IllegalStateException: falhou para a***@exemplo.com")
                    .contains("Caused by: java.lang.RuntimeException: token ");
        }

        @Test
        @DisplayName("as linhas \"at\" e \"... frames omitted\" ficam intactas (pilha legível)")
        void linhasDaPilha() {
            String mascarada = MascaraDeLog.mascararPilha(PILHA);

            assertThat(mascarada)
                    .contains("\tat br.com.coe.servicos.usuario.ServicoDeLogin.entrar(ServicoDeLogin.java:61)")
                    .contains(
                            "UsernamePasswordAuthenticationFilter.attempt(UsernamePasswordAuthenticationFilter.java:85)")
                    .contains("\t... 12 common frames omitted")
                    .contains("\tat br.com.coe.servicos.Algo1234567890Abcdefghijklmnopqrstu.metodo(Algo.java:10)");
        }
    }
}
