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

/** Ligar e desligar o MFA (opcional para cliente e profissional; ADMIN não desliga). */
class ConfigurarMfaTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private String pedirCodigo(Conta conta, String bearer) throws Exception {
        mockMvc.perform(post("/api/contas/eu/mfa/codigo").header("Authorization", bearer))
                .andExpect(status().isAccepted());
        return sms.ultimoCodigo(conta.celular()).orElseThrow();
    }

    private ResultActions definir(String bearer, boolean ativo, String codigo) throws Exception {
        return mockMvc.perform(post("/api/contas/eu/mfa")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\":" + ativo + ",\"codigo\":\"" + codigo + "\"}"));
    }

    private boolean mfaLigado(Conta conta) {
        return jdbc.queryForObject("SELECT mfa_sms_ativo FROM usuario WHERE id = ?", Boolean.class, conta.id());
    }

    @Test
    @DisplayName("liga e desliga com o código SMS")
    void ligaEDesliga() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);

        definir(bearer, true, pedirCodigo(conta, bearer)).andExpect(status().isNoContent());
        assertThat(mfaLigado(conta)).isTrue();

        relogio.avancar(java.time.Duration.ofSeconds(61));
        definir(bearer, false, pedirCodigo(conta, bearer)).andExpect(status().isNoContent());
        assertThat(mfaLigado(conta)).isFalse();
    }

    @Test
    @DisplayName("código errado: 422 e nada muda")
    void codigoErrado() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);
        String codigo = pedirCodigo(conta, bearer);

        definir(bearer, true, codigo.equals("000000") ? "111111" : "000000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:codigo-invalido"));
        assertThat(mfaLigado(conta)).isFalse();
    }

    @Test
    @DisplayName("sem celular confirmado: 403 celular-nao-confirmado")
    void exigeCelularConfirmado() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        mockMvc.perform(post("/api/contas/eu/mfa/codigo").header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-nao-confirmado"));
    }

    @Test
    @DisplayName("ADMIN não desliga o MFA: 403 mfa-obrigatorio")
    void adminNaoDesliga() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String bearer = bearer(admin.id(), Papel.ADMIN);

        definir(bearer, false, pedirCodigo(admin, bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:mfa-obrigatorio"));
    }
}
