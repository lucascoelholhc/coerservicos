package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import br.com.coe.servicos.IntegracaoTest;

/** Usuário, papel e aceite dos termos são gravados juntos: se o aceite falha, nada fica. */
class CadastroClienteAtomicidadeTest extends IntegracaoTest {

    @MockitoSpyBean
    AceiteTermosRepository aceites;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("falha ao gravar o aceite desfaz o usuário e o papel")
    void falhaNoAceiteDesfazTudo() throws Exception {
        doThrow(new IllegalStateException("falha simulada")).when(aceites).save(any());

        mockMvc.perform(post("/api/contas/cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Ana Silva","celular":"47900000951","email":"atomico951@teste.coe.local",\
                                "cep":"89010000","senha":"Casa-Azul-2026","versaoTermosAceita":"1.0"}"""))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE celular = '47900000951'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario_papel p JOIN usuario u ON u.id = p.usuario_id"
                                + " WHERE u.celular = '47900000951'",
                        Integer.class))
                .isZero();
    }
}
