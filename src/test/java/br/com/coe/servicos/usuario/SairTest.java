package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** POST /api/auth/sair e /api/auth/sair-de-todos (PA03). */
class SairTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    record Sessao(String access, String refresh) {}

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private Sessao entrar(Conta conta) throws Exception {
        MockHttpServletResponse resposta = mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return new Sessao(
                JsonPath.read(resposta.getContentAsString(), "$.accessToken"), LoginTest.refreshDoCookie(resposta));
    }

    private int renovar(String refresh) throws Exception {
        return mockMvc.perform(post("/api/auth/renovar").cookie(new Cookie("coe_refresh", refresh)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    @DisplayName("sair: revoga a sessão deste aparelho e apaga o cookie")
    void sair() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        Sessao celular = entrar(conta);
        Sessao notebook = entrar(conta);

        MockHttpServletResponse resposta = mockMvc.perform(post("/api/auth/sair")
                        .header("Authorization", "Bearer " + celular.access())
                        .cookie(new Cookie("coe_refresh", celular.refresh())))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse();

        assertThat(resposta.getHeader("Set-Cookie")).startsWith("coe_refresh=;").contains("Max-Age=0");
        assertThat(renovar(celular.refresh())).isEqualTo(401);
        assertThat(renovar(notebook.refresh())).isEqualTo(200);
    }

    @Test
    @DisplayName("sair de todos: nenhum aparelho renova mais")
    void sairDeTodos() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        Sessao celular = entrar(conta);
        Sessao notebook = entrar(conta);

        mockMvc.perform(post("/api/auth/sair-de-todos").header("Authorization", "Bearer " + celular.access()))
                .andExpect(status().isNoContent());

        assertThat(renovar(celular.refresh())).isEqualTo(401);
        assertThat(renovar(notebook.refresh())).isEqualTo(401);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND motivo_revogacao = 'sair_todos'",
                        Integer.class,
                        conta.id()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("sair com o cookie de outra pessoa não derruba a sessão dela (anti-IDOR)")
    void sairComCookieAlheio() throws Exception {
        Sessao minha = entrar(contas.criar(Papel.CLIENTE));
        Sessao alheia = entrar(contas.criar(Papel.CLIENTE));

        mockMvc.perform(post("/api/auth/sair")
                        .header("Authorization", "Bearer " + minha.access())
                        .cookie(new Cookie("coe_refresh", alheia.refresh())))
                .andExpect(status().isNoContent());

        assertThat(renovar(alheia.refresh())).isEqualTo(200);
    }

    @Test
    @DisplayName("sair concorrendo com a renovação do mesmo aparelho: nenhum token da família sobra válido")
    void sairConcorrendoComRenovar() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        Sessao sessao = entrar(conta);
        java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<?> saida = executor.submit(() -> {
            largada.await();
            return mockMvc.perform(post("/api/auth/sair")
                            .header("Authorization", "Bearer " + sessao.access())
                            .cookie(new Cookie("coe_refresh", sessao.refresh())))
                    .andReturn();
        });
        java.util.concurrent.Future<?> renovacao = executor.submit(() -> {
            largada.await();
            return mockMvc.perform(post("/api/auth/renovar").cookie(new Cookie("coe_refresh", sessao.refresh())))
                    .andReturn();
        });
        largada.countDown();
        saida.get();
        renovacao.get();
        executor.shutdown();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revogado_em IS NULL",
                        Integer.class,
                        conta.id()))
                .isZero();
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() throws Exception {
        mockMvc.perform(post("/api/auth/sair")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/sair-de-todos")).andExpect(status().isUnauthorized());
    }
}
