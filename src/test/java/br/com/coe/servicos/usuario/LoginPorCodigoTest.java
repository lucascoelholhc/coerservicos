package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** Login só com código SMS (RF01): celular confirmado, nunca ADMIN nem quem tem MFA (seria um fator só). */
class LoginPorCodigoTest extends IntegracaoTest {

    private static final String MENSAGEM_202 = "Se o celular tiver conta, enviamos um código por SMS.";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private ResultActions pedirCodigo(String celular) throws Exception {
        return mockMvc.perform(post("/api/auth/codigo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\"}"));
    }

    private ResultActions entrarComCodigo(String celular, String codigo) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar-com-codigo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    @Test
    @DisplayName("celular confirmado: pede o código (202) e entra com ele")
    void entraComCodigo() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);

        pedirCodigo(conta.celular())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_202));
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();

        entrarComCodigo(conta.celular(), codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(conta.id().toString()));
        entrarComCodigo(conta.celular(), codigo).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("celular que não existe: o mesmo 202, sem SMS")
    void celularInexistente() throws Exception {
        pedirCodigo("47999998888")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_202));

        assertThat(sms.para("47999998888")).isEmpty();
    }

    @Test
    @DisplayName("pedir de novo antes de 60 s: o mesmo 202, sem segundo SMS")
    void limiteRespondeIgual() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        pedirCodigo(conta.celular()).andExpect(status().isAccepted());

        pedirCodigo(conta.celular())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_202));
        assertThat(sms.para(conta.celular())).hasSize(1);
    }

    @Test
    @DisplayName("celular não confirmado, ADMIN ou MFA ligado: 202 sem SMS e 401 ao tentar entrar")
    void naoElegivel() throws Exception {
        Conta naoConfirmada = contas.criar(Papel.CLIENTE);
        Conta admin = contas.criar(Papel.ADMIN);
        Conta comMfa = contas.criar("ativo", true, Papel.PROFISSIONAL);

        for (Conta conta : java.util.List.of(naoConfirmada, admin, comMfa)) {
            pedirCodigo(conta.celular()).andExpect(status().isAccepted());
            assertThat(sms.para(conta.celular())).as(conta.nome()).isEmpty();
            entrarComCodigo(conta.celular(), "123456")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:coe:erro:login-invalido"));
        }
    }

    @Test
    @DisplayName("ADMIN não entra com um código válido de outra finalidade (fator único proibido)")
    void adminNuncaEntraSoComCodigo() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + admin.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"))
                .andExpect(status().isForbidden());
        String codigoDoMfa = sms.ultimoCodigo(admin.celular()).orElseThrow();

        entrarComCodigo(admin.celular(), codigoDoMfa).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("código errado: 401")
    void codigoErrado() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        pedirCodigo(conta.celular());
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();

        entrarComCodigo(conta.celular(), codigo.equals("000000") ? "111111" : "000000")
                .andExpect(status().isUnauthorized());
    }
}
