package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.http.Cookie;

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

/**
 * Senha (CORE-05; RF01): esqueci (202 sempre igual; link só para e-mail confirmado, senão SMS no
 * celular confirmado), redefinir (revoga todas as sessões, não entra) e trocar (mantém a atual).
 */
@ExtendWith(OutputCaptureExtension.class)
class SenhaTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(80_000);
    private static final String NOVA = "Outra-Senha-2026";
    private static final String MENSAGEM = "Se houver uma conta com esse login, enviamos as instruções.";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private Conta comEmailConfirmado() {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET email_verificado_em = now() WHERE id = ?", conta.id());
        return conta;
    }

    private ResultActions esqueci(String login) throws Exception {
        return mockMvc.perform(post("/api/auth/senha/esqueci")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\"}"));
    }

    private ResultActions redefinirComLink(String token, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/senha/redefinir")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + senha + "\"}"));
    }

    private ResultActions redefinirComCodigo(String celular, String codigo, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/senha/redefinir")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo + "\",\"novaSenha\":\"" + senha
                        + "\"}"));
    }

    private MockHttpServletResponse entrar(String login, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn()
                .getResponse();
    }

    private static Cookie refresh(MockHttpServletResponse resposta) {
        return resposta.getCookie(CookieDeRenovacao.NOME);
    }

    private int renovar(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/renovar")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .cookie(cookie))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<String> motivos(Conta conta) {
        return jdbc.queryForList(
                "SELECT coalesce(motivo_revogacao, 'ativo') FROM refresh_token WHERE usuario_id = ?",
                String.class,
                conta.id());
    }

    // ---------------------------------------------------------------- esqueci

    @Test
    @DisplayName("esqueci: o mesmo 202 para conta existente, inexistente e suspensa; só a existente recebe")
    void esqueciIgualParaTodos() throws Exception {
        Conta conta = comEmailConfirmado();
        Conta suspensa = contas.criar("suspenso", false, Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET email_verificado_em = now() WHERE id = ?", suspensa.id());

        esqueci(conta.email())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));
        esqueci("ninguem@teste.coe.local")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));
        esqueci(suspensa.email())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));

        assertThat(email.ultimoLink(conta.email()))
                .hasValueSatisfying(
                        link -> assertThat(link).startsWith("http://localhost:5173/redefinir-senha#token="));
        assertThat(email.para("ninguem@teste.coe.local")).isEmpty();
        assertThat(email.para(suspensa.email())).isEmpty();
    }

    @Test
    @DisplayName("esqueci: e-mail não confirmado nunca recebe o link; vai SMS se o celular for confirmado")
    void emailNaoConfirmadoNaoRecebe() throws Exception {
        Conta comCelular = contas.criarConfirmada(Papel.CLIENTE);
        Conta semNada = contas.criar(Papel.CLIENTE);

        esqueci(comCelular.email()).andExpect(status().isAccepted());
        esqueci(semNada.celular()).andExpect(status().isAccepted());

        assertThat(email.para(comCelular.email())).isEmpty();
        assertThat(sms.ultimoCodigo(comCelular.celular())).isPresent();
        assertThat(email.para(semNada.email())).isEmpty();
        assertThat(sms.para(semNada.celular())).isEmpty();
    }

    @Test
    @DisplayName("esqueci exige o Origin do front (como todo POST de /api/auth menos o entrar)")
    void esqueciExigeOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/senha/esqueci")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"x@teste.coe.local\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- redefinir

    @Test
    @DisplayName("redefinir por link: troca a senha, revoga todas as sessões (troca_senha) e não entra")
    void redefinirPorLink(CapturedOutput saida) throws Exception {
        Conta conta = comEmailConfirmado();
        entrar(conta.celular(), ContasDeTeste.SENHA);
        entrar(conta.email(), ContasDeTeste.SENHA);
        esqueci(conta.email());
        String token = email.ultimoToken(conta.email()).orElseThrow();

        redefinirComLink(token, NOVA).andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(motivos(conta)).hasSize(2).containsOnly("troca_senha");
        assertThat(entrar(conta.celular(), ContasDeTeste.SENHA).getStatus()).isEqualTo(401);
        assertThat(entrar(conta.celular(), NOVA).getStatus()).isEqualTo(200);
        assertThat(saida.getAll()).doesNotContain(token).doesNotContain(NOVA);
    }

    @Test
    @DisplayName("redefinir por código SMS (conta sem e-mail confirmado)")
    void redefinirPorCodigo() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        esqueci(conta.celular());
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();

        redefinirComCodigo(conta.celular(), codigo, NOVA).andExpect(status().isNoContent());

        assertThat(entrar(conta.celular(), NOVA).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("link reusado ou vencido (30 min), código errado: 422 link-invalido")
    void linkInvalido() throws Exception {
        Conta conta = comEmailConfirmado();
        esqueci(conta.email());
        String token = email.ultimoToken(conta.email()).orElseThrow();
        redefinirComLink(token, NOVA).andExpect(status().isNoContent());
        redefinirComLink(token, "Mais-Uma-Senha-9")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:link-invalido"));

        Conta outra = comEmailConfirmado();
        esqueci(outra.email());
        String vencido = email.ultimoToken(outra.email()).orElseThrow();
        relogio.avancar(Duration.ofMinutes(31));
        redefinirComLink(vencido, NOVA).andExpect(status().isUnprocessableEntity());

        Conta porSms = contas.criarConfirmada(Papel.CLIENTE);
        esqueci(porSms.celular());
        String codigo = sms.ultimoCodigo(porSms.celular()).orElseThrow();
        redefinirComCodigo(porSms.celular(), codigo.equals("000000") ? "111111" : "000000", NOVA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:link-invalido"));
    }

    @Test
    @DisplayName("senha fraca na redefinição: 422 senha-nao-permitida e o link continua valendo")
    void senhaFracaNaRedefinicao() throws Exception {
        Conta conta = comEmailConfirmado();
        esqueci(conta.email());
        String token = email.ultimoToken(conta.email()).orElseThrow();

        redefinirComLink(token, conta.celular())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:senha-nao-permitida"));
        redefinirComLink(token, "12345678").andExpect(status().isUnprocessableEntity());
        redefinirComLink(token, NOVA).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("redefinir não confirma contato nenhum e não mexe no MFA")
    void redefinirNaoMexeEmContatoNemMfa() throws Exception {
        Conta conta = contas.criar("ativo", true, Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET email_verificado_em = now() WHERE id = ?", conta.id());
        String antes = jdbc.queryForObject(
                "SELECT celular_verificado_em::text || email_verificado_em::text || mfa_sms_ativo::text FROM usuario WHERE id = ?",
                String.class,
                conta.id());
        esqueci(conta.email());

        redefinirComLink(email.ultimoToken(conta.email()).orElseThrow(), NOVA).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                        "SELECT celular_verificado_em::text || email_verificado_em::text || mfa_sms_ativo::text FROM usuario WHERE id = ?",
                        String.class,
                        conta.id()))
                .isEqualTo(antes);
    }

    // ---------------------------------------------------------------- trocar (logado)

    private ResultActions trocar(String bearer, Cookie cookie, String atual, String nova) throws Exception {
        var requisicao = post("/api/auth/senha/trocar")
                .header("Origin", ORIGEM_DO_FRONT)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}");
        return mockMvc.perform(cookie == null ? requisicao : requisicao.cookie(cookie));
    }

    @Test
    @DisplayName("trocar: mantém a sessão deste aparelho e revoga as outras")
    void trocarMantemAAtual() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        MockHttpServletResponse esteAparelho = entrar(conta.celular(), ContasDeTeste.SENHA);
        MockHttpServletResponse outroAparelho = entrar(conta.email(), ContasDeTeste.SENHA);
        String bearer = "Bearer " + JsonPath.read(esteAparelho.getContentAsString(), "$.accessToken");

        trocar(bearer, refresh(esteAparelho), ContasDeTeste.SENHA, NOVA).andExpect(status().isNoContent());

        assertThat(renovar(refresh(esteAparelho))).isEqualTo(200);
        assertThat(renovar(refresh(outroAparelho))).isEqualTo(401);
        assertThat(entrar(conta.celular(), NOVA).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("trocar sem o cookie do aparelho: revoga todas as sessões")
    void trocarSemCookieRevogaTodas() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        MockHttpServletResponse aparelho = entrar(conta.celular(), ContasDeTeste.SENHA);
        String bearer = "Bearer " + JsonPath.read(aparelho.getContentAsString(), "$.accessToken");

        trocar(bearer, null, ContasDeTeste.SENHA, NOVA).andExpect(status().isNoContent());

        assertThat(renovar(refresh(aparelho))).isEqualTo(401);
    }

    @Test
    @DisplayName("trocar: senha atual errada = 422 senha-atual-incorreta; nova fraca = 422 senha-nao-permitida")
    void trocarRecusado() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);

        trocar(bearer, null, "Errada-Senha-1", NOVA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:senha-atual-incorreta"));
        trocar(bearer, null, ContasDeTeste.SENHA, conta.email())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:senha-nao-permitida"));
        assertThat(entrar(conta.celular(), ContasDeTeste.SENHA).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("trocar exige login")
    void trocarExigeLogin() throws Exception {
        mockMvc.perform(post("/api/auth/senha/trocar")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"a\",\"novaSenha\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- cadastro manda a confirmação do e-mail

    private static String novoCelular() {
        return String.valueOf(47_900_000_000L + SEQUENCIA.incrementAndGet());
    }

    private ResultActions cadastrar(String celular, String endereco) throws Exception {
        return mockMvc.perform(post("/api/contas/cliente")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Pessoa Nova\",\"celular\":\"" + celular + "\",\"email\":\"" + endereco
                        + "\",\"cep\":\"89010000\",\"senha\":\"Casa-Azul-2026\",\"versaoTermosAceita\":\"1.0\"}"));
    }

    @Test
    @DisplayName("o cadastro manda o link de confirmação do e-mail sozinho, depois do commit")
    void cadastroMandaConfirmacao() throws Exception {
        String endereco = "cad-" + SEQUENCIA.incrementAndGet() + "@teste.coe.local";

        cadastrar(novoCelular(), endereco).andExpect(status().isCreated());

        String token = email.ultimoToken(endereco).orElseThrow();
        mockMvc.perform(post("/api/contas/email/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("provedor de e-mail fora no cadastro: o 201 sai do mesmo jeito")
    void cadastroComProvedorFora() throws Exception {
        String endereco = "cad-" + SEQUENCIA.incrementAndGet() + "@teste.coe.local";
        email.falharProximo();

        cadastrar(novoCelular(), endereco).andExpect(status().isCreated());

        assertThat(email.para(endereco)).isEmpty();
    }
}
