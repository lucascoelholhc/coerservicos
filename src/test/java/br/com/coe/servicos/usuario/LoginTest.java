package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** POST /api/auth/entrar (CORE-03; RF01, RN58, PA03). */
@ExtendWith(OutputCaptureExtension.class)
class LoginTest extends IntegracaoTest {

    static final String MENSAGEM_401 = "Login ou senha incorretos.";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    ResultActions entrar(String login, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0")
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"));
    }

    static String mascarado(String celular) {
        return "(" + celular.substring(0, 2) + ") " + celular.substring(2, 7) + "-" + celular.substring(7);
    }

    static String refreshDoCookie(MockHttpServletResponse resposta) {
        String cookie = resposta.getHeader("Set-Cookie");
        assertThat(cookie).as("Set-Cookie do refresh").startsWith("coe_refresh=");
        return cookie.substring("coe_refresh=".length(), cookie.indexOf(';'));
    }

    @Test
    @DisplayName("login por celular com máscara: tokens, cookie do refresh e sessão gravada")
    void entraPorCelular() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        MockHttpServletResponse resposta = entrar(mascarado(conta.celular()), ContasDeTeste.SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").value("2026-10-05T12:15:00Z"))
                .andExpect(jsonPath("$.usuario.id").value(conta.id().toString()))
                .andExpect(jsonPath("$.usuario.nome").value(conta.nome()))
                .andExpect(jsonPath("$.usuario.papeis[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn()
                .getResponse();

        String cookie = resposta.getHeader("Set-Cookie");
        assertThat(cookie)
                .contains("Path=/api/auth", "Max-Age=2592000", "HttpOnly", "SameSite=Strict", "Secure")
                .doesNotContain("Domain=");
        String refresh = refreshDoCookie(resposta);
        Map<String, Object> sessao = jdbc.queryForMap(
                "SELECT token_hash, aparelho, host(ip) AS ip, expira_em, familia_id FROM refresh_token"
                        + " WHERE usuario_id = ?",
                conta.id());
        assertThat((byte[]) sessao.get("token_hash")).hasSize(32);
        assertThat(new String((byte[]) sessao.get("token_hash"), StandardCharsets.ISO_8859_1))
                .isNotEqualTo(refresh);
        assertThat(sessao.get("aparelho").toString()).startsWith("Mozilla/5.0");
        assertThat(sessao.get("ip")).isEqualTo("127.0.0.1");
        assertThat(jdbc.queryForObject(
                        "SELECT ultimo_login_em IS NOT NULL FROM usuario WHERE id = ?", Boolean.class, conta.id()))
                .isTrue();
    }

    @Test
    @DisplayName("login por celular com +55 e por e-mail em maiúsculas")
    void entraPorCelularComPaisEPorEmail() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        entrar("+55 " + mascarado(conta.celular()), ContasDeTeste.SENHA).andExpect(status().isOk());
        entrar("  " + conta.email().toUpperCase() + " ", ContasDeTeste.SENHA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o token não carrega celular, e-mail nem nome")
    void tokenSemDadoPessoal() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        String corpo = entrar(conta.email(), ContasDeTeste.SENHA)
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(corpo, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(claims).contains(conta.id().toString()).doesNotContain(conta.celular(), conta.email(), conta.nome());
    }

    @Test
    @DisplayName(
            "senha errada, login inexistente, senha acima de 72 bytes, conta excluída ou login inválido: o mesmo 401")
    void mesmo401() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        Conta excluida = contas.criar("excluido", false, Papel.CLIENTE);

        String senhaErrada = corpo401(conta.celular(), "Outra-Senha-1");
        String inexistente = corpo401("47999999999", ContasDeTeste.SENHA);
        String emailInexistente = corpo401("ninguem@teste.coe.local", ContasDeTeste.SENHA);
        String longa = corpo401(conta.celular(), "é".repeat(40));
        String daExcluida = corpo401(excluida.celular(), ContasDeTeste.SENHA);
        String invalido = corpo401("abc", ContasDeTeste.SENHA);

        assertThat(senhaErrada).contains("urn:coe:erro:login-invalido").contains(MENSAGEM_401);
        assertThat(java.util.List.of(inexistente, emailInexistente, longa, daExcluida, invalido))
                .allSatisfy(corpo -> assertThat(corpo).isEqualTo(senhaErrada));
    }

    private String corpo401(String login, String senha) throws Exception {
        return entrar(login, senha)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("conta suspensa: 403 com a senha certa, 401 com a errada")
    void contaSuspensa() throws Exception {
        Conta conta = contas.criar("suspenso", false, Papel.PROFISSIONAL);

        entrar(conta.celular(), ContasDeTeste.SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-suspensa"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        entrar(conta.celular(), "Outra-Senha-1").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("conta em análise entra normalmente (restrições de chat e contrato ficam no DOM-05)")
    void contaEmAnalise() throws Exception {
        Conta conta = contas.criar("em_analise", false, Papel.CLIENTE);

        entrar(conta.celular(), ContasDeTeste.SENHA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MFA ligado ou ADMIN: 403 segundo-passo-necessario, sem tokens nem sessão")
    void segundoPasso() throws Exception {
        Conta comMfa = contas.criar("ativo", true, Papel.CLIENTE);
        Conta admin = contas.criar(Papel.ADMIN);

        for (Conta conta : java.util.List.of(comMfa, admin)) {
            entrar(conta.celular(), ContasDeTeste.SENHA)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:coe:erro:segundo-passo-necessario"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(header().doesNotExist("Set-Cookie"));
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM refresh_token WHERE usuario_id = ?", Integer.class, conta.id()))
                    .isZero();
        }
        entrar(admin.celular(), "Outra-Senha-1").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("hash antigo (o $2b$ do seed) é regravado como {bcrypt} custo 12 e o novo login funciona")
    void regravaHashAntigo() throws Exception {
        Conta conta = contas.criarComHash(ContasDeTeste.HASH_DO_SEED, Papel.CLIENTE);

        entrar(conta.celular(), ContasDeTeste.SENHA_DO_SEED).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id = ?", String.class, conta.id()))
                .startsWith("{bcrypt}$2a$12$");
        entrar(conta.celular(), ContasDeTeste.SENHA_DO_SEED).andExpect(status().isOk());
    }

    @Test
    @DisplayName("entrar com um access token inválido no header ainda funciona")
    void entrarComBearerInvalido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        mockMvc.perform(post("/api/auth/entrar")
                        .header("Authorization", "Bearer token.vencido.ou.invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("login ou senha em branco: 400")
    void emBranco() throws Exception {
        entrar(" ", ContasDeTeste.SENHA).andExpect(status().isBadRequest());
        entrar("47900000001", "").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("log só com o id e o resultado: nunca login, senha, token ou cookie")
    void logSemDadoPessoal(CapturedOutput saida) throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        MockHttpServletResponse resposta =
                entrar(conta.email(), ContasDeTeste.SENHA).andReturn().getResponse();
        entrar(conta.celular(), "Senha-Errada-99").andExpect(status().isUnauthorized());
        String token = JsonPath.read(resposta.getContentAsString(), "$.accessToken");

        assertThat(saida.getAll())
                .contains(conta.id().toString())
                .doesNotContain(
                        conta.celular(),
                        conta.email(),
                        ContasDeTeste.SENHA,
                        "Senha-Errada-99",
                        token,
                        refreshDoCookie(resposta));
    }

    @Test
    @DisplayName("o id do usuário no token é o mesmo da resposta")
    void idNoToken() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String corpo = entrar(conta.celular(), ContasDeTeste.SENHA)
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(corpo, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(claims).contains("\"sub\":\"" + conta.id() + "\"");
        assertThat(UUID.fromString(JsonPath.read(corpo, "$.usuario.id"))).isEqualTo(conta.id());
    }
}
