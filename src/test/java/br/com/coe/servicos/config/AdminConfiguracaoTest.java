package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * Configuração de negócio pelo admin (DOM-11; RN31, RN52, RN59): nova vigência só com valor válido pela mesma regra da
 * subida, cache invalidado na hora e auditoria com antes e depois. A tabela é só de inserção e o banco dos testes é
 * compartilhado: todo teste que muda um valor devolve o original no fim (outra vigência).
 */
class AdminConfiguracaoTest extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    @Autowired
    private PasswordEncoder codificador;

    private UUID admin;

    @BeforeEach
    void criarAdmin() {
        admin = new ContasDeTeste(jdbc, codificador)
                .criarConfirmada(Papel.ADMIN)
                .id();
    }

    private ResultActions mudar(String chave, String valor) throws Exception {
        return mockMvc.perform(put("/api/admin/configuracao/" + chave)
                .header("Authorization", bearer(admin, Papel.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":\"" + valor + "\"}"));
    }

    @Test
    @DisplayName("lista os valores em vigor com descrição; só ADMIN")
    void lista() throws Exception {
        mockMvc.perform(get("/api/admin/configuracao").header("Authorization", bearer(admin, Papel.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.itens[?(@.chave == 'COMISSAO')].descricao").isNotEmpty())
                .andExpect(
                        jsonPath("$.itens[?(@.chave == 'FALTAS_ALERTA')].valor").value("2"));
        mockMvc.perform(get("/api/admin/configuracao").header("Authorization", bearer(admin, Papel.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("mudar: nova vigência, cache invalidado na hora e auditoria com antes e depois")
    void mudar() throws Exception {
        try {
            mudar("FALTAS_ALERTA", "3")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value("3"));
            assertThat(configuracao.faltasAlerta()).isEqualTo(3);
            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT antes::text AS antes, depois::text AS depois FROM log_auditoria"
                            + " WHERE ator_id = ? AND acao = 'configuracao.alterar'",
                    admin);
            assertThat(linha.get("antes").toString()).contains("FALTAS_ALERTA").contains("\"2\"");
            assertThat(linha.get("depois").toString()).contains("\"3\"");
            assertThat(jdbc.queryForObject(
                            "SELECT criado_por FROM configuracao WHERE chave = 'FALTAS_ALERTA'"
                                    + " ORDER BY vigente_desde DESC LIMIT 1",
                            UUID.class))
                    .isEqualTo(admin);
        } finally {
            mudar("FALTAS_ALERTA", "2").andExpect(status().isOk());
        }
        assertThat(configuracao.faltasAlerta()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "valor inválido pela regra da subida: 422 e nada muda (comissão acima de 30%, LC 150 acima de 2, texto)")
    void valorInvalido() throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM configuracao", Long.class);
        mudar("COMISSAO", "0.35")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:configuracao-invalida"));
        mudar("LIMITE_DOMESTICO_SEMANA", "3").andExpect(status().isUnprocessableEntity());
        mudar("TAXA_PAGA_POR", "ninguem").andExpect(status().isUnprocessableEntity());
        mudar("AUTO_LIBERA_HORAS", "abc").andExpect(status().isUnprocessableEntity());
        // teto: valor enorme estouraria as contas de data (revisão de segurança)
        mudar("FALTAS_JANELA_DIAS", "2000000000").andExpect(status().isUnprocessableEntity());
        mudar("AUTO_LIBERA_HORAS", "721").andExpect(status().isUnprocessableEntity());
        mudar("NAO_EXISTE", "1").andExpect(status().isNotFound());
        mudar("COMISSAO", "0.10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:configuracao-igual"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM configuracao", Long.class))
                .isEqualTo(antes);
    }
}
