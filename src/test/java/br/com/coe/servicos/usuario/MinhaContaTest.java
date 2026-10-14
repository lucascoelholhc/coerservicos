package br.com.coe.servicos.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/** GET /api/contas/eu (CORE-06): os dados do próprio usuário, identificado só pelo token. */
class MinhaContaTest extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    @Test
    @DisplayName("com token: os dados do próprio usuário, sem hash nem status interno, sem cache")
    void proprioUsuario() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conta.id().toString()))
                .andExpect(jsonPath("$.nome").value(conta.nome()))
                .andExpect(jsonPath("$.papeis[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.email").value(conta.email()))
                .andExpect(jsonPath("$.celular").value(conta.celular()))
                .andExpect(jsonPath("$.celularConfirmado").value(false))
                .andExpect(jsonPath("$.emailConfirmado").value(false))
                .andExpect(jsonPath("$.contatoPendente").value(false))
                .andExpect(jsonPath("$.mfaAtivo").value(false))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.mfaSmsAtivo").doesNotExist())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("celular confirmado aparece como confirmado")
    void celularConfirmado() throws Exception {
        Conta conta = contas.criar(Papel.PROFISSIONAL);
        jdbc.update("UPDATE usuario SET celular_verificado_em = now() WHERE id = ?", conta.id());

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(conta.id(), Papel.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.celularConfirmado").value(true));
    }

    @Test
    @DisplayName("e-mail confirmado e código no login ligado aparecem (para a tela de segurança)")
    void emailConfirmadoEMfaAtivo() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update(
                "UPDATE usuario SET email_verificado_em = now(), celular_verificado_em = now(), mfa_sms_ativo = true WHERE id = ?",
                conta.id());

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(true))
                .andExpect(jsonPath("$.celularConfirmado").value(true))
                .andExpect(jsonPath("$.mfaAtivo").value(true));
    }

    @Test
    @DisplayName("conta que perdeu o e-mail para o dono (RN61): contato pendente, lido do banco")
    void contatoPendente() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET email = NULL WHERE id = ?", conta.id());

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.contatoPendente").value(true));
    }

    @Test
    @DisplayName("conta que perdeu o celular para o dono (RN61): contato pendente")
    void contatoPendenteSemCelular() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET celular = NULL WHERE id = ?", conta.id());

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.celular").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.contatoPendente").value(true));
    }

    @Test
    @DisplayName("o usuário vem do token, nunca da URL: B nunca vê os dados de A")
    void nuncaDadosDeOutro() throws Exception {
        Conta a = contas.criar(Papel.CLIENTE);
        Conta b = contas.criar(Papel.CLIENTE);

        mockMvc.perform(get("/api/contas/eu")
                        .param("id", a.id().toString())
                        .param("usuarioId", a.id().toString())
                        .header("Authorization", bearer(b.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(b.id().toString()))
                .andExpect(jsonPath("$.email").value(b.email()));
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() throws Exception {
        mockMvc.perform(get("/api/contas/eu")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token de um usuário que não existe mais: 404")
    void usuarioInexistente() throws Exception {
        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(UUID.randomUUID(), Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-encontrado"));
    }

    @Test
    void toStringNaoMostraDadosPessoais() {
        String texto = new MinhaContaResponse(
                        java.util.UUID.randomUUID(),
                        "Ana Souza",
                        java.util.List.of(Papel.CLIENTE),
                        "ana@exemplo.com",
                        "47900000001",
                        true,
                        true,
                        false,
                        false)
                .toString();

        org.assertj.core.api.Assertions.assertThat(texto)
                .doesNotContain("ana@exemplo.com")
                .doesNotContain("47900000001")
                .doesNotContain("Ana Souza");
    }
}
