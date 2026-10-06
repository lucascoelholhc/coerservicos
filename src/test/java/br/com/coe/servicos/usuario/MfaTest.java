package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** MFA por SMS (CORE-04): obrigatório para ADMIN, opcional para os demais; tokens só no segundo passo. */
@ExtendWith(OutputCaptureExtension.class)
class MfaTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private ResultActions entrar(Conta conta) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"));
    }

    private String desafioDe(Conta conta) throws Exception {
        String corpo = entrar(conta)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:segundo-passo-necessario"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(corpo, "$.desafioId");
    }

    private ResultActions segundoPasso(String desafio, String codigo) throws Exception {
        return mockMvc.perform(post("/api/auth/segundo-passo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafioId\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private static String errado(String codigo) {
        return codigo.equals("000000") ? "111111" : "000000";
    }

    @Test
    @DisplayName("ADMIN: a senha certa manda o SMS e pede o segundo passo; o código certo emite os tokens")
    void adminSempreComSegundoPasso() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String desafio = desafioDe(admin);
        String codigo = sms.ultimoCodigo(admin.celular()).orElseThrow();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_token WHERE usuario_id = ?", Integer.class, admin.id()))
                .as("nenhuma sessão antes do segundo passo")
                .isZero();
        segundoPasso(desafio, codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.papeis[0]").value("ADMIN"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("coe_refresh=")));
    }

    @Test
    @DisplayName("usuário com MFA ligado também pede o segundo passo")
    void usuarioComMfa() throws Exception {
        Conta conta = contas.criar("ativo", true, Papel.CLIENTE);
        String desafio = desafioDe(conta);

        segundoPasso(desafio, sms.ultimoCodigo(conta.celular()).orElseThrow()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("desafio reusado, código errado ou desafio inventado: 401 genérico")
    void desafioInvalido() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String desafio = desafioDe(admin);
        String codigo = sms.ultimoCodigo(admin.celular()).orElseThrow();

        segundoPasso(desafio, errado(codigo)).andExpect(status().isUnauthorized());
        segundoPasso("desafio-inventado", codigo).andExpect(status().isUnauthorized());
        segundoPasso(desafio, codigo).andExpect(status().isOk());
        segundoPasso(desafio, codigo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:login-invalido"));
    }

    @Test
    @DisplayName("5 códigos errados invalidam o desafio")
    void cincoErrosInvalidam() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String desafio = desafioDe(admin);
        String codigo = sms.ultimoCodigo(admin.celular()).orElseThrow();
        for (int i = 0; i < 5; i++) {
            segundoPasso(desafio, errado(codigo)).andExpect(status().isUnauthorized());
        }

        segundoPasso(desafio, codigo).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("desafio vencido (5 min): 401")
    void desafioVencido() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String desafio = desafioDe(admin);
        relogio.avancar(Duration.ofMinutes(5).plusSeconds(1));

        segundoPasso(desafio, sms.ultimoCodigo(admin.celular()).orElseThrow()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("segundo passo sem Origin: 403 (CSRF de login)")
    void segundoPassoExigeOrigem() throws Exception {
        mockMvc.perform(post("/api/auth/segundo-passo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafioId\":\"x\",\"codigo\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN com celular não confirmado: 403 celular-nao-confirmado (não tem para onde mandar)")
    void adminSemCelularConfirmado() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        jdbc.update("UPDATE usuario SET celular_verificado_em = NULL WHERE id = ?", admin.id());

        entrar(admin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-nao-confirmado"));
        assertThat(sms.para(admin.celular())).isEmpty();
    }

    @Test
    @DisplayName("log sem código, desafio nem celular")
    void logSemSegredo(CapturedOutput saida) throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String desafio = desafioDe(admin);
        String codigo = sms.ultimoCodigo(admin.celular()).orElseThrow();
        segundoPasso(desafio, codigo).andExpect(status().isOk());

        assertThat(saida.getAll()).doesNotContain(desafio, admin.celular()).doesNotContain("é " + codigo);
    }
}
