package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Contador de falhas (decisão de 07/10): 10 códigos errados em 24 h na mesma conta bloqueiam toda
 * entrada por código por 1 h. Login por código responde igual a sempre; o segundo passo do MFA, 429.
 */
@ExtendWith(OutputCaptureExtension.class)
class BloqueioPorFalhasTest extends IntegracaoTest {

    private static final Duration ENTRE_ENVIOS = Duration.ofSeconds(61);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    ServicoDeCodigoSms codigos;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private static String errado(String codigo) {
        return codigo.equals("000000") ? "111111" : "000000";
    }

    /** Envia um código de login e erra {@code vezes} (no máximo 5 por código). */
    private String errarLogin(Conta conta, int vezes) {
        assertThat(codigos.enviar(conta.id(), conta.celular(), FinalidadeSms.LOGIN, "127.0.0.1"))
                .isTrue();
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        for (int i = 0; i < vezes; i++) {
            assertThat(codigos.conferir(conta.celular(), FinalidadeSms.LOGIN, errado(codigo)))
                    .isFalse();
        }
        relogio.avancar(ENTRE_ENVIOS);
        return codigo;
    }

    private ResultActions pedirCodigo(Conta conta) throws Exception {
        return mockMvc.perform(post("/api/auth/codigo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + conta.celular() + "\"}"));
    }

    private ResultActions entrarComCodigo(Conta conta, String codigo) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar-com-codigo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + conta.celular() + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private ResultActions entrarComSenha(Conta conta) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"));
    }

    private ResultActions segundoPasso(String desafio, String codigo) throws Exception {
        return mockMvc.perform(post("/api/auth/segundo-passo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafioId\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    /** Pede o código e entra com ele; devolve a resposta do entrar. */
    private ResultActions pedirEEntrar(Conta conta) throws Exception {
        pedirCodigo(conta).andExpect(status().isAccepted());
        return entrarComCodigo(conta, sms.ultimoCodigo(conta.celular()).orElseThrow());
    }

    @Test
    @DisplayName("o código certo não soma tentativa (tentativas conta só erros)")
    void certoNaoSoma() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);

        pedirEEntrar(conta).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT tentativas FROM codigo_sms WHERE celular = ?", Integer.class, conta.celular()))
                .isZero();
    }

    @Test
    @DisplayName("9 erros não bloqueiam")
    void noveErrosNaoBloqueiam() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 4);

        pedirEEntrar(conta).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o 10º erro bloqueia: login por código responde igual a sempre e nenhum SMS sai")
    void decimoErroBloqueia(CapturedOutput saida) throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 5);
        int smsAntes = sms.para(conta.celular()).size();

        pedirCodigo(conta).andExpect(status().isAccepted());
        assertThat(sms.para(conta.celular())).as("bloqueado: nada é enviado").hasSize(smsAntes);
        entrarComCodigo(conta, "123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:login-invalido"));
        assertThat(saida.getAll())
                .contains("Entrada por código bloqueada")
                .contains(conta.id().toString())
                .doesNotContain(conta.celular());
    }

    @Test
    @DisplayName("bloqueado, o código certo de um envio anterior ao bloqueio também não entra")
    void bloqueadoNemComOCodigoCerto() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 4);
        codigos.enviar(conta.id(), conta.celular(), FinalidadeSms.LOGIN, "127.0.0.1");
        String certo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        codigos.conferir(conta.celular(), FinalidadeSms.LOGIN, errado(certo)); // 10º erro

        entrarComCodigo(conta, certo).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("depois de 1 h, libera")
    void liberaDepoisDeUmaHora() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 5);
        relogio.avancar(Duration.ofHours(1));

        pedirEEntrar(conta).andExpect(status().isOk());
    }

    @Test
    @DisplayName("erros de 25 h atrás não contam")
    void errosAntigosNaoContam() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 4);
        relogio.avancar(Duration.ofHours(25));
        errarLogin(conta, 1);

        pedirEEntrar(conta).andExpect(status().isOk());
    }

    @Test
    @DisplayName("soma as finalidades: 5 erros ligando o MFA + 5 no login por código bloqueiam")
    void somaAsFinalidades() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        codigos.enviar(conta.id(), conta.celular(), FinalidadeSms.CONFIGURAR_MFA, "127.0.0.1");
        String codigoMfa = sms.ultimoCodigo(conta.celular()).orElseThrow();
        for (int i = 0; i < 5; i++) {
            codigos.conferir(conta.celular(), FinalidadeSms.CONFIGURAR_MFA, errado(codigoMfa));
        }
        relogio.avancar(ENTRE_ENVIOS);
        errarLogin(conta, 5);

        pedirCodigo(conta).andExpect(status().isAccepted());
        entrarComCodigo(conta, "123456").andExpect(status().isUnauthorized());
        relogio.avancar(Duration.ofHours(1));
        pedirEEntrar(conta).andExpect(status().isOk());
    }

    @Test
    @DisplayName("bloqueado, o login com senha (sem MFA) continua funcionando")
    void senhaSemMfaContinua() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarLogin(conta, 5);
        errarLogin(conta, 5);

        entrarComSenha(conta).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MFA: o 10º erro bloqueia o segundo passo com 429, mesmo com o código certo")
    void segundoPassoBloqueado() throws Exception {
        Conta conta = contas.criar("ativo", true, Papel.CLIENTE);
        for (int rodada = 0; rodada < 2; rodada++) {
            String desafio = JsonPath.read(
                    entrarComSenha(conta)
                            .andExpect(status().isForbidden())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(),
                    "$.desafioId");
            String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();
            int erros = rodada == 0 ? 5 : 4;
            for (int i = 0; i < erros; i++) {
                segundoPasso(desafio, errado(codigo)).andExpect(status().isUnauthorized());
            }
            relogio.avancar(ENTRE_ENVIOS);
        }
        String desafio =
                JsonPath.read(entrarComSenha(conta).andReturn().getResponse().getContentAsString(), "$.desafioId");
        String certo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        segundoPasso(desafio, errado(certo)).andExpect(status().isUnauthorized()); // 10º erro

        segundoPasso(desafio, certo)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"));
        relogio.avancar(ENTRE_ENVIOS);
        entrarComSenha(conta)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"));
    }
}
