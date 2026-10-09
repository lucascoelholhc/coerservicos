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

/** Confirmar o e-mail por link (CORE-04, RN61): token só como hash, 24 h, uso único, no fragmento. */
@ExtendWith(OutputCaptureExtension.class)
class ConfirmarEmailTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private ResultActions pedirLink(Conta conta) throws Exception {
        return mockMvc.perform(
                post("/api/contas/eu/email/confirmacao").header("Authorization", bearer(conta.id(), Papel.CLIENTE)));
    }

    private ResultActions confirmar(String token) throws Exception {
        return mockMvc.perform(post("/api/contas/email/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private boolean confirmado(Conta conta) {
        return jdbc.queryForObject(
                "SELECT email_verificado_em IS NOT NULL FROM usuario WHERE id = ?", Boolean.class, conta.id());
    }

    private String tokenEnviado(Conta conta) throws Exception {
        pedirLink(conta).andExpect(status().isAccepted());
        return email.ultimoToken(conta.email()).orElseThrow();
    }

    @Test
    @DisplayName("o link certo confirma; o link leva o token no fragmento, nunca na query string")
    void linkCertoConfirma() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String token = tokenEnviado(conta);

        assertThat(email.ultimoLink(conta.email()))
                .hasValueSatisfying(link -> assertThat(link)
                        .startsWith("http://localhost:5173/confirmar-email#token=")
                        .doesNotContain("?"));
        confirmar(token).andExpect(status().isNoContent());
        assertThat(confirmado(conta)).isTrue();
    }

    @Test
    @DisplayName("no banco só o SHA-256 do token (32 bytes); o token em claro não aparece em lugar nenhum")
    void soOHashNoBanco() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String token = tokenEnviado(conta);

        assertThat(jdbc.queryForObject(
                        "SELECT octet_length(token_hash) FROM token_verificacao WHERE usuario_id = ?",
                        Integer.class,
                        conta.id()))
                .isEqualTo(32);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM token_verificacao WHERE usuario_id = ? AND destino LIKE ?",
                        Integer.class,
                        conta.id(),
                        "%" + token + "%"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT destino FROM token_verificacao WHERE usuario_id = ?", String.class, conta.id()))
                .isEqualTo(conta.email());
    }

    @Test
    @DisplayName("reusado, vencido, inventado ou de um e-mail que mudou: 422 link-invalido, mesma mensagem")
    void linkInvalido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String token = tokenEnviado(conta);
        confirmar(token).andExpect(status().isNoContent());
        String detalhe = JsonPath.read(
                confirmar(token)
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.type").value("urn:coe:erro:link-invalido"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.detail");

        Conta vencida = contas.criar(Papel.CLIENTE);
        String tokenVencido = tokenEnviado(vencida);
        relogio.avancar(Duration.ofHours(24).plusSeconds(1));
        confirmar(tokenVencido)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(detalhe));
        assertThat(confirmado(vencida)).isFalse();

        confirmar("inventado")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(detalhe));

        Conta trocou = contas.criar(Papel.CLIENTE);
        String tokenDoEmailAntigo = tokenEnviado(trocou);
        jdbc.update("UPDATE usuario SET email = ? WHERE id = ?", "novo" + trocou.email(), trocou.id());
        confirmar(tokenDoEmailAntigo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(detalhe));
        assertThat(confirmado(trocou)).isFalse();
    }

    @Test
    @DisplayName("um link novo invalida o anterior")
    void novoInvalidaOAnterior() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String primeiro = tokenEnviado(conta);
        relogio.avancar(Duration.ofSeconds(61));
        String segundo = tokenEnviado(conta);

        confirmar(primeiro).andExpect(status().isUnprocessableEntity());
        confirmar(segundo).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("limites por endereço: 1 a cada 60 s e 5 por hora (429)")
    void limites() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        pedirLink(conta).andExpect(status().isAccepted());
        pedirLink(conta)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"));
        for (int i = 0; i < 4; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            pedirLink(conta).andExpect(status().isAccepted());
        }
        relogio.avancar(Duration.ofSeconds(61));
        pedirLink(conta).andExpect(status().isTooManyRequests());
        assertThat(email.para(conta.email())).hasSize(5);
    }

    @Test
    @DisplayName("limite: a 6ª mensagem na hora, misturando finalidades (confirmar e posse), não envia")
    void sextaNaHoraMisturandoFinalidades() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String posse = "{\"email\":\"" + conta.email() + "\"}";
        for (int rodada = 0; rodada < 3; rodada++) {
            pedirLink(conta).andExpect(status().isAccepted());
            if (rodada < 2) {
                mockMvc.perform(post("/api/contas/posse/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(posse))
                        .andExpect(status().isAccepted());
            }
            relogio.avancar(Duration.ofSeconds(61));
        }
        assertThat(email.para(conta.email()))
                .as("confirmar e posse no mesmo minuto: os dois saem")
                .hasSize(5);

        mockMvc.perform(post("/api/contas/posse/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(posse))
                .andExpect(status().isAccepted());

        assertThat(email.para(conta.email())).as("a 6ª na hora não sai").hasSize(5);
    }

    @Test
    @DisplayName("e-mail já confirmado: 409 e nenhum e-mail")
    void jaConfirmado() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        confirmar(tokenEnviado(conta)).andExpect(status().isNoContent());
        relogio.avancar(Duration.ofSeconds(61));

        pedirLink(conta)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:email-ja-confirmado"));
        assertThat(email.para(conta.email())).hasSize(1);
    }

    @Test
    @DisplayName("a resposta não espera o provedor de e-mail")
    void naoEsperaOEnvio() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        email.atrasar(Duration.ofSeconds(2));

        long inicio = System.nanoTime();
        pedirLink(conta).andExpect(status().isAccepted());

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(1));
        assertThat(email.ultimoToken(conta.email())).isPresent();
    }

    @Test
    @DisplayName("log sem token, link nem e-mail inteiro")
    void logSemSegredo(CapturedOutput saida) throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String token = tokenEnviado(conta);
        confirmar(token).andExpect(status().isNoContent());

        assertThat(saida.getAll()).doesNotContain(token).doesNotContain(conta.email());
    }

    @Test
    @DisplayName("pedir o link exige login; confirmar é público")
    void autorizacao() throws Exception {
        mockMvc.perform(post("/api/contas/eu/email/confirmacao")).andExpect(status().isUnauthorized());
        confirmar("qualquer").andExpect(status().isUnprocessableEntity());
    }
}
