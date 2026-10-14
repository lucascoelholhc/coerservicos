package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * CORE-07: limites por login, celular e usuário (os que dependem do corpo ou do token). Chave sempre
 * em HMAC: login, celular e e-mail nunca aparecem em claro na limite_balde.
 */
class LimitesPorContaTest extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder codificador;

    private ContasDeTeste contas;

    @org.junit.jupiter.api.BeforeEach
    void contas() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private ResultActions entrar(String login, String senha, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"));
    }

    @Test
    @DisplayName("login + IP: a 9ª tentativa em 15 min = 429, mesmo com a senha certa")
    void loginEIp() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        for (int i = 0; i < 8; i++) {
            entrar(conta.email(), "Senha-Errada-1", "203.0.113.40").andExpect(status().isUnauthorized());
        }

        entrar(conta.email(), ContasDeTeste.SENHA, "203.0.113.40")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"))
                .andExpect(header().exists("Retry-After"));
        entrar(conta.email(), ContasDeTeste.SENHA, "203.0.113.41").andExpect(status().isOk());
    }

    @Test
    @DisplayName("o mesmo login digitado de outro jeito (maiúsculas, espaço) cai no mesmo balde")
    void loginNormalizado() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        for (int i = 0; i < 8; i++) {
            entrar(conta.email(), "Senha-Errada-1", "203.0.113.42");
        }

        entrar("  " + conta.email().toUpperCase() + " ", ContasDeTeste.SENHA, "203.0.113.42")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("teto por login (20/h, qualquer IP): a 21ª = 429 e uma auditoria só, sem o login em claro")
    void tetoPorLogin() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        for (int i = 0; i < 20; i++) {
            entrar(conta.email(), "Senha-Errada-1", "198.51.100." + (i / 5)).andExpect(status().isUnauthorized());
        }

        entrar(conta.email(), ContasDeTeste.SENHA, "198.51.100.200").andExpect(status().isTooManyRequests());
        entrar(conta.email(), ContasDeTeste.SENHA, "198.51.100.201").andExpect(status().isTooManyRequests());

        List<Map<String, Object>> auditoria = jdbc.queryForList(
                "SELECT entidade, entidade_id, antes::text AS antes, depois::text AS depois, host(ip) AS ip"
                        + " FROM log_auditoria WHERE acao = 'limite.teto-login'");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.getFirst()).containsEntry("entidade", "usuario").containsEntry("entidade_id", conta.id());
        assertThat(auditoria.getFirst().toString())
                .doesNotContain(conta.email())
                .doesNotContain(conta.celular());
    }

    @Test
    @DisplayName("entrar com código: o 11º pedido do mesmo celular em 10 min = 429, de qualquer IP")
    void entrarComCodigoPorCelular() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        for (int i = 0; i < 10; i++) {
            entrarComCodigo(conta.celular(), "000000", "198.51.100." + i).andExpect(status().isUnauthorized());
        }

        entrarComCodigo(conta.celular(), "000000", "198.51.100.50").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("trocar senha (por usuário): a 6ª em 15 min = 429; outro usuário não é afetado")
    void trocarSenhaPorUsuario() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        Conta outra = contas.criar(Papel.CLIENTE);
        for (int i = 0; i < 5; i++) {
            trocarSenha(conta, "Senha-Errada-1").andExpect(status().isUnprocessableEntity());
        }

        trocarSenha(conta, "Senha-Errada-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        trocarSenha(outra, "Senha-Errada-1").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("nenhuma chave da limite_balde tem login, celular ou e-mail em claro")
    void chavesSemDadoPessoal() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        entrar(conta.email(), "Senha-Errada-1", "203.0.113.43");
        entrar(conta.celular(), "Senha-Errada-1", "203.0.113.43");
        entrarComCodigo(conta.celular(), "000000", "203.0.113.43");

        assertThat(jdbc.queryForList("SELECT chave FROM limite_balde", String.class))
                .isNotEmpty()
                .noneMatch(chave -> chave.contains(conta.email()) || chave.contains(conta.celular()));
    }

    private ResultActions entrarComCodigo(String celular, String codigo, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar-com-codigo")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private ResultActions trocarSenha(Conta conta, String senhaAtual) throws Exception {
        return mockMvc.perform(post("/api/auth/senha/trocar")
                .header("Authorization", bearer(conta.id(), Papel.CLIENTE))
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + senhaAtual + "\",\"novaSenha\":\"Outra-Senha-2026\"}"));
    }
}
