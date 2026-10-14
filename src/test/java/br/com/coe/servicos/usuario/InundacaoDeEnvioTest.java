package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * CORE-07: inundação de prova de posse (fila normal travada e cheia) não impede o código de login de
 * sair (fila prioritária). As mensagens descartadas devolvem a cota.
 */
class InundacaoDeEnvioTest extends IntegracaoTest {

    private static final int PEDIDOS = 60;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private void pedirPosse(String celular, int pedido) throws Exception {
        mockMvc.perform(post("/api/contas/posse/celular")
                        .with(requisicao -> {
                            requisicao.setRemoteAddr("203.0.113." + (60 + pedido / 9));
                            return requisicao;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + celular + "\"}"))
                .andExpect(status().isAccepted());
    }

    /** Celulares cujo código de posse foi descartado (e invalidado) pela fila cheia. */
    private java.util.List<String> descartados(java.util.List<String> alvos) {
        String lugares = String.join(",", java.util.Collections.nCopies(alvos.size(), "?"));
        return jdbc.queryForList(
                "SELECT celular FROM codigo_sms WHERE finalidade = 'comprovar_posse' AND descartado_em IS NOT NULL"
                        + " AND invalidado_em IS NOT NULL AND celular IN (" + lugares + ")",
                String.class,
                alvos.toArray());
    }

    @Test
    @DisplayName("posse em massa com a fila normal travada: o código de login sai e o descarte devolve a cota")
    void inundacaoDePosse() throws Exception {
        ContasDeTeste contas = new ContasDeTeste(jdbc, codificador);
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        // a posse só manda SMS para celular de outra conta, ainda não confirmado (RN61)
        String hash = codificador.encode(ContasDeTeste.SENHA);
        java.util.List<String> alvos = java.util.stream.IntStream.range(0, PEDIDOS)
                .mapToObj(i -> contas.criarComHash(hash, Papel.CLIENTE).celular())
                .toList();
        sms.prender(alvos::contains);
        try {
            for (int i = 0; i < PEDIDOS; i++) {
                pedirPosse(alvos.get(i), i);
            }

            mockMvc.perform(post("/api/auth/codigo")
                            .header("Origin", ORIGEM_DO_FRONT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"celular\":\"" + conta.celular() + "\"}"))
                    .andExpect(status().isAccepted());

            assertThat(sms.aguardarCodigo(conta.celular())).isPresent();
            assertThat(descartados(alvos)).isNotEmpty();
        } finally {
            sms.soltar();
        }
        String descartado = descartados(alvos).getFirst();

        pedirPosse(descartado, 0); // a cota voltou: sem a espera de 60 s

        assertThat(sms.para(descartado)).hasSize(1);
    }
}
