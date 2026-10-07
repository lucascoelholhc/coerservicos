package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** Confirmar o celular (RN08): obrigatório para profissional, opcional para cliente. */
class ConfirmarCelularTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private boolean confirmado(Conta conta) {
        return jdbc.queryForObject(
                "SELECT celular_verificado_em IS NOT NULL FROM usuario WHERE id = ?", Boolean.class, conta.id());
    }

    @Test
    @DisplayName("pede o código e confirma com ele")
    void confirma() throws Exception {
        Conta conta = contas.criar(Papel.PROFISSIONAL);
        String bearer = bearer(conta.id(), Papel.PROFISSIONAL);

        mockMvc.perform(post("/api/contas/eu/celular/codigo").header("Authorization", bearer))
                .andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        mockMvc.perform(post("/api/contas/eu/celular/confirmar")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(confirmado(conta)).isTrue();
    }

    @Test
    @DisplayName("código errado ou vencido: 422 codigo-invalido e o celular continua não confirmado")
    void codigoInvalido() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);
        mockMvc.perform(post("/api/contas/eu/celular/codigo").header("Authorization", bearer));
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        relogio.avancar(Duration.ofMinutes(6));

        mockMvc.perform(post("/api/contas/eu/celular/confirmar")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:codigo-invalido"));
        assertThat(confirmado(conta)).isFalse();
    }

    @Test
    @DisplayName("celular já confirmado: 409 e nenhum SMS")
    void jaConfirmado() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);

        mockMvc.perform(post("/api/contas/eu/celular/codigo")
                        .header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-confirmado"));
        assertThat(sms.para(conta.celular())).isEmpty();
    }

    @Test
    @DisplayName("pedir de novo antes de 60 s: 429")
    void limite() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);
        mockMvc.perform(post("/api/contas/eu/celular/codigo").header("Authorization", bearer))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/contas/eu/celular/codigo").header("Authorization", bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"));
    }

    @Test
    @DisplayName("conta suspensa (token ainda válido): 403 conta-suspensa e nenhum SMS")
    void contaSuspensa() throws Exception {
        Conta conta = contas.criar("suspenso", false, Papel.CLIENTE);

        mockMvc.perform(post("/api/contas/eu/celular/codigo")
                        .header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conta-suspensa"));
        assertThat(sms.para(conta.celular())).isEmpty();
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() throws Exception {
        mockMvc.perform(post("/api/contas/eu/celular/codigo")).andExpect(status().isUnauthorized());
    }
}
