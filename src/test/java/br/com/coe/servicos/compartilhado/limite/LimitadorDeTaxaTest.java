package br.com.coe.servicos.compartilhado.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;

/**
 * CORE-07: limitador com os baldes no Postgres (Bucket4j). Regras {@code teste-*} do
 * application-test.yml. Falha do banco recusa (fail-closed): trava disputada = 429 com espera curta;
 * banco fora = 503. O lock_timeout curto vale só na transação do limitador.
 */
class LimitadorDeTaxaTest extends IntegracaoTest {

    private static final String IP = "203.0.113.7";

    @Autowired
    private LimitadorDeTaxa limitador;

    @Autowired
    private PropriedadesDeLimites propriedades;

    @Autowired
    private JdbcConnectionDetails banco;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSourceDaAplicacao;

    @Nested
    @DisplayName("balde por chave")
    class PorChave {

        @Test
        @DisplayName("passa até a capacidade e recusa a seguinte com a espera até repor")
        void capacidadeEEspera() {
            for (int i = 0; i < 3; i++) {
                limitador.consumir(Balde.porChave("teste-ip", IP));
            }

            MuitasTentativasException recusa = catchThrowableOfType(
                    MuitasTentativasException.class, () -> limitador.consumir(Balde.porChave("teste-ip", IP)));

            assertThat(recusa.espera()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
        }

        @Test
        @DisplayName("chaves diferentes não dividem o balde")
        void chavesSeparadas() {
            for (int i = 0; i < 3; i++) {
                limitador.consumir(Balde.porChave("teste-ip", IP));
            }

            assertThatCode(() -> limitador.consumir(Balde.porChave("teste-ip", "203.0.113.8")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o balde enche de novo pelo Clock da aplicação")
        void reposicaoPeloClock() {
            for (int i = 0; i < 3; i++) {
                limitador.consumir(Balde.porChave("teste-ip", IP));
            }
            relogio.avancar(Duration.ofMinutes(1));

            assertThatCode(() -> limitador.consumir(Balde.porChave("teste-ip", IP)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a chave gravada é regra + chave (quem chama manda dado pessoal já em HMAC)")
        void chaveGravada() {
            limitador.consumir(Balde.porChave("teste-ip", IP));

            assertThat(jdbc.queryForList("SELECT chave FROM limite_balde", String.class))
                    .containsExactly("teste-ip:" + IP);
        }

        @Test
        @DisplayName("regra que não existe na configuração é erro de programação")
        void regraDesconhecida() {
            assertThatThrownBy(() -> limitador.consumir(Balde.porChave("nao-existe", IP)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("balde global dividido em fatias (H1)")
    class Global {

        @Test
        @DisplayName("o IP é cobrado antes do global: quem passa do limite do IP não gasta o global")
        void ipAntesDoGlobal() {
            for (int i = 0; i < 5; i++) {
                try {
                    limitador.consumir(Balde.porChave("teste-ordem-ip", IP), Balde.global("teste-ordem-global"));
                } catch (MuitasTentativasException recusadoNoIp) {
                    // 2 passam, 3 param no IP
                }
            }
            int passaram = 0;
            for (int i = 0; i < 20; i++) {
                try {
                    limitador.consumir(
                            Balde.porChave("teste-ordem-ip", "198.51.100." + i), Balde.global("teste-ordem-global"));
                    passaram++;
                } catch (MuitasTentativasException recusadoNoGlobal) {
                    // global esgotado
                }
            }

            assertThat(passaram).isEqualTo(8); // 10 do global - 2 que passaram pelo IP
        }

        @Test
        @DisplayName("concorrência: nunca passa do total, só recusa com 429 e usa uma linha por fatia")
        void concorrencia() throws Exception {
            ExecutorService threads = Executors.newFixedThreadPool(16);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Boolean>> pedidos = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                pedidos.add(threads.submit(() -> {
                    largada.await();
                    try {
                        limitador.consumir(Balde.global("teste-global"));
                        return true;
                    } catch (MuitasTentativasException recusado) {
                        return false;
                    }
                }));
            }
            largada.countDown();
            int passaram = 0;
            for (Future<Boolean> pedido : pedidos) {
                passaram += Boolean.TRUE.equals(pedido.get(20, TimeUnit.SECONDS)) ? 1 : 0;
            }
            threads.shutdown();

            assertThat(passaram).isBetween(1, 40);
            assertThat(jdbc.queryForList("SELECT chave FROM limite_balde ORDER BY chave", String.class))
                    .allMatch(chave -> chave.matches("teste-global:global:[0-3]"))
                    .hasSizeLessThanOrEqualTo(4);
        }
    }

    @Nested
    @DisplayName("falha do banco: recusa, nunca deixa passar sem contar")
    class FalhaDoBanco {

        @Test
        @DisplayName("trava disputada além do lock_timeout = 429 com espera curta, rápido")
        void lockTimeout() throws SQLException {
            limitador.consumir(Balde.porChave("teste-ip", IP));
            try (Connection outra = dataSourceDaAplicacao.getConnection()) {
                outra.setAutoCommit(false);
                try (Statement trava = outra.createStatement()) {
                    trava.execute("SELECT * FROM limite_balde WHERE chave = 'teste-ip:" + IP + "' FOR UPDATE");
                }
                long inicio = System.nanoTime();

                MuitasTentativasException recusa = catchThrowableOfType(
                        MuitasTentativasException.class, () -> limitador.consumir(Balde.porChave("teste-ip", IP)));

                assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
                assertThat(recusa.espera()).isEqualTo(propriedades.esperaNaDisputa());
                outra.rollback();
            }
        }

        @Test
        @DisplayName("banco fora = serviço instável (503)")
        void bancoFora() {
            DataSource fora = new HikariDataSource(configuracao(1, "jdbc:postgresql://127.0.0.1:1/nada"));
            LimitadorDeTaxa semBanco = new LimitadorDeTaxa(fora, propriedades, relogioDoTeste());

            assertThatThrownBy(() -> semBanco.consumir(Balde.porChave("teste-ip", IP)))
                    .isInstanceOf(ServicoInstavelException.class);
            ((HikariDataSource) fora).close();
        }

        @Test
        @DisplayName("o lock_timeout é SET LOCAL: a conexão volta ao pool sem ele")
        void lockTimeoutNaoVaza() throws SQLException {
            try (HikariDataSource umaConexao = new HikariDataSource(configuracao(1, banco.getJdbcUrl()))) {
                LimitadorDeTaxa comUmaConexao = new LimitadorDeTaxa(umaConexao, propriedades, relogioDoTeste());
                comUmaConexao.consumir(Balde.porChave("teste-ip", IP));

                try (Connection mesma = umaConexao.getConnection();
                        Statement consulta = mesma.createStatement();
                        var resultado = consulta.executeQuery("SHOW lock_timeout")) {
                    resultado.next();
                    assertThat(resultado.getString(1)).isEqualTo("0");
                }
            }
        }

        @Test
        @DisplayName("uma transação comum da aplicação depois do limitador não herda o lock_timeout")
        void transacaoComumSemTimeout() {
            limitador.consumir(Balde.porChave("teste-ip", IP));

            assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("0");
        }

        private HikariConfig configuracao(int conexoes, String url) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(banco.getUsername());
            config.setPassword(banco.getPassword());
            config.setMaximumPoolSize(conexoes);
            config.setConnectionTimeout(500);
            config.setInitializationFailTimeout(-1);
            return config;
        }

        private java.time.Clock relogioDoTeste() {
            return relogio;
        }
    }

    @Nested
    @DisplayName("configuração")
    class Configuracao {

        @Test
        @DisplayName("capacidade zero impede a subida")
        void capacidadeZero() {
            assertThatThrownBy(() -> new PropriedadesDeLimites.Regra(0, Duration.ofMinutes(1), 1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("mais fatias do que a capacidade impede a subida (cada fatia precisa de pelo menos 1)")
        void fatiasDemais() {
            assertThatThrownBy(() -> new PropriedadesDeLimites.Regra(3, Duration.ofMinutes(1), 4))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
