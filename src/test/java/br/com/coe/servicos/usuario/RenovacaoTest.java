package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** POST /api/auth/renovar (PA03): refresh rotativo, reuso revoga a família. */
class RenovacaoTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private String entrar(Conta conta) throws Exception {
        MockHttpServletResponse resposta = mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return LoginTest.refreshDoCookie(resposta);
    }

    private ResultActions renovar(String refresh) throws Exception {
        return mockMvc.perform(
                post("/api/auth/renovar").header("Origin", ORIGEM_DO_FRONT).cookie(new Cookie("coe_refresh", refresh)));
    }

    private String renovarComSucesso(String refresh) throws Exception {
        return LoginTest.refreshDoCookie(
                renovar(refresh).andExpect(status().isOk()).andReturn().getResponse());
    }

    private void recusada(String refresh) throws Exception {
        MockHttpServletResponse resposta = renovar(refresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:sessao-expirada"))
                .andReturn()
                .getResponse();
        assertThat(resposta.getHeader("Set-Cookie")).startsWith("coe_refresh=;").contains("Max-Age=0");
    }

    private int revogadosDaFamilia(Conta conta, String motivo) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND motivo_revogacao = ?",
                Integer.class,
                conta.id(),
                motivo);
    }

    @Test
    @DisplayName("renovação válida: novo access, novo cookie, antigo marcado como usado e ligado ao sucessor")
    void renovaValido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        relogio.avancar(Duration.ofMinutes(20));

        MockHttpServletResponse resposta = renovar(refresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").value("2026-10-05T12:35:00Z"))
                .andExpect(jsonPath("$.usuario.id").value(conta.id().toString()))
                .andReturn()
                .getResponse();

        String novo = LoginTest.refreshDoCookie(resposta);
        assertThat(novo).isNotEqualTo(refresh);
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT familia_id) FROM refresh_token WHERE usuario_id = ?",
                        Integer.class,
                        conta.id()))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND usado_em IS NOT NULL"
                                + " AND substituido_por IS NOT NULL",
                        Integer.class,
                        conta.id()))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT max(expira_em) = timestamptz '2026-11-04T12:20:00Z' FROM refresh_token"
                                + " WHERE usuario_id = ?",
                        Boolean.class,
                        conta.id()))
                .as("o sucessor vale 30 dias a partir de agora")
                .isTrue();
        renovarComSucesso(novo);
    }

    @Test
    @DisplayName("sem cookie ou com cookie inválido: 401")
    void semCookie() throws Exception {
        mockMvc.perform(post("/api/auth/renovar").header("Origin", ORIGEM_DO_FRONT))
                .andExpect(status().isUnauthorized());
        recusada("isso-nao-e-um-token");
        recusada("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    }

    @Test
    @DisplayName("reuso até 30 s depois (duas abas): 401 sem revogar a família")
    void reusoBenigno() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        String sucessor = renovarComSucesso(refresh);
        relogio.avancar(Duration.ofSeconds(10));

        MockHttpServletResponse resposta = renovar(refresh)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        assertThat(resposta.getHeader("Set-Cookie"))
                .as("não pode apagar o cookie novo que a outra aba acabou de receber")
                .isNull();
        assertThat(revogadosDaFamilia(conta, "reuso")).isZero();
        renovarComSucesso(sucessor);
    }

    @Test
    @DisplayName("reuso depois de 30 s (sinal de roubo): revoga a família inteira")
    void reusoRevogaAFamilia() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        String sucessor = renovarComSucesso(refresh);
        relogio.avancar(Duration.ofSeconds(31));

        recusada(refresh);

        assertThat(revogadosDaFamilia(conta, "reuso"))
                .as("o usado e o sucessor")
                .isEqualTo(2);
        recusada(sucessor);
    }

    @Test
    @DisplayName("reuso não derruba os outros aparelhos do mesmo usuário")
    void reusoSoNaFamilia() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String celular = entrar(conta);
        String notebook = entrar(conta);
        renovarComSucesso(celular);
        relogio.avancar(Duration.ofMinutes(1));

        recusada(celular);

        renovarComSucesso(notebook);
    }

    @Test
    @DisplayName("teto: com 89 dias de sessão ainda renova (renovando a cada 29 dias)")
    void teto89Dias() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofDays(29));
            refresh = renovarComSucesso(refresh);
        }
        relogio.avancar(Duration.ofDays(2));

        renovarComSucesso(refresh);

        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT sessao_iniciada_em) FROM refresh_token WHERE usuario_id = ?",
                        Integer.class,
                        conta.id()))
                .as("todos os sucessores herdam o início da sessão")
                .isOne();
    }

    @Test
    @DisplayName("teto: com 90 dias e 1 s de sessão recusa, revoga a família e apaga o cookie")
    void teto90DiasRecusa() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofDays(29));
            refresh = renovarComSucesso(refresh);
        }
        relogio.avancar(Duration.ofDays(3).plusSeconds(1));

        recusada(refresh);

        assertThat(revogadosDaFamilia(conta, "teto"))
                .as("a família inteira: 3 usados e o atual")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("teto: exatamente 90 dias recusa; 90 dias menos 1 s ainda renova")
    void tetoLimiteExato() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofDays(29));
            refresh = renovarComSucesso(refresh);
        }
        relogio.avancar(Duration.ofDays(3).minusSeconds(1));
        refresh = renovarComSucesso(refresh);
        relogio.avancar(Duration.ofSeconds(1));

        recusada(refresh);
    }

    @Test
    @DisplayName("teto: renovação no dia 75 gera refresh e cookie de 15 dias (nunca além dos 90)")
    void renovacaoNoDia75() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        relogio.avancar(Duration.ofDays(29));
        refresh = renovarComSucesso(refresh);
        relogio.avancar(Duration.ofDays(29));
        refresh = renovarComSucesso(refresh);
        relogio.avancar(Duration.ofDays(17));

        MockHttpServletResponse resposta =
                renovar(refresh).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getHeader("Set-Cookie"))
                .contains("Max-Age=" + Duration.ofDays(15).toSeconds());
        assertThat(jdbc.queryForObject(
                        "SELECT max(expira_em) = timestamptz '2027-01-03T12:00:00Z' FROM refresh_token WHERE usuario_id = ?",
                        Boolean.class,
                        conta.id()))
                .as("o último refresh vence junto com a sessão (início + 90 dias)")
                .isTrue();
    }

    @Test
    @DisplayName("refresh vencido (30 dias): 401")
    void vencido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        relogio.avancar(Duration.ofDays(30).plusSeconds(1));

        recusada(refresh);
    }

    @Test
    @DisplayName("conta suspensa depois do login: a renovação falha e a família é revogada")
    void contaSuspensa() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        jdbc.update("UPDATE usuario SET status = 'suspenso' WHERE id = ?", conta.id());

        recusada(refresh);

        assertThat(revogadosDaFamilia(conta, "admin")).isOne();
    }

    @Test
    @DisplayName("conta excluída depois do login: a renovação falha e a família é revogada")
    void contaExcluida() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        jdbc.update("UPDATE usuario SET status = 'excluido', excluido_em = now() WHERE id = ?", conta.id());

        recusada(refresh);

        assertThat(revogadosDaFamilia(conta, "admin")).isOne();
    }

    @Test
    @DisplayName("renovar com um access token vencido no header ainda funciona (o front pode mandá-lo)")
    void renovarComBearerVencido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);

        mockMvc.perform(post("/api/auth/renovar")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .header("Authorization", "Bearer token.vencido.ou.invalido")
                        .cookie(new Cookie("coe_refresh", refresh)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("duas renovações ao mesmo tempo: uma passa, a outra recebe 401 e a família continua")
    void concorrencia() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = entrar(conta);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<MockHttpServletResponse>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(executor.submit(() -> {
                largada.await();
                return renovar(refresh).andReturn().getResponse();
            }));
        }
        largada.countDown();
        List<MockHttpServletResponse> respostas = new ArrayList<>();
        for (Future<MockHttpServletResponse> resultado : resultados) {
            respostas.add(resultado.get());
        }
        executor.shutdown();

        assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 401);
        assertThat(respostas)
                .filteredOn(resposta -> resposta.getStatus() == 401)
                .allSatisfy(
                        perdedor -> assertThat(perdedor.getHeader("Set-Cookie")).isNull());
        assertThat(revogadosDaFamilia(conta, "reuso")).isZero();
    }
}
