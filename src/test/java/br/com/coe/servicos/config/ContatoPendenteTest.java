package br.com.coe.servicos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.seguranca.LiberadoComContatoPendente;
import br.com.coe.servicos.usuario.Papel;

/**
 * Contato pendente (RN61): com a marca no token, nega por padrão; só passa o que tem
 * {@link LiberadoComContatoPendente} (no DOM-08: Cheguei, Terminei o dia, aprovar diária).
 */
@Import(ContatoPendenteTest.ControladorDeExecucao.class)
class ContatoPendenteTest extends IntegracaoTest {

    /** Endpoints fictícios: um de execução (liberado) e um de começar algo novo (bloqueado). */
    @RestController
    @RequestMapping("/teste/contato-pendente")
    static class ControladorDeExecucao {

        @PostMapping("/terminei-o-dia")
        @PreAuthorize("isAuthenticated()")
        @LiberadoComContatoPendente
        void execucao() {}

        @PostMapping("/contratar")
        @PreAuthorize("isAuthenticated()")
        void comecarAlgoNovo() {}
    }

    @Test
    @DisplayName("com a marca: o liberado passa, o resto é 403 contato-pendente")
    void comAMarca() throws Exception {
        String bearer = bearerComContatoPendente(UUID.randomUUID(), Papel.PROFISSIONAL);

        mockMvc.perform(post("/teste/contato-pendente/terminei-o-dia").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/teste/contato-pendente/contratar").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contato-pendente"));
        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer))
                .andExpect(status().isNotFound()); // liberado: chega ao serviço (conta inexistente = 404)
    }

    @Test
    @DisplayName("sem a marca: tudo segue normal")
    void semAMarca() throws Exception {
        String bearer = bearer(UUID.randomUUID(), Papel.CLIENTE);

        mockMvc.perform(post("/teste/contato-pendente/contratar").header("Authorization", bearer))
                .andExpect(status().isOk());
    }
}
