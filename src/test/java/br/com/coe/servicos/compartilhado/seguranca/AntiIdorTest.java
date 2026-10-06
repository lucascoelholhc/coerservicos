package br.com.coe.servicos.compartilhado.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.usuario.Papel;

/**
 * Padrão anti-IDOR (CORE-06) provado com um recurso que só existe neste teste: o dono vem do
 * token ({@link UsuarioAutenticado}), a busca já filtra pelo dono e recurso de outro usuário é 404,
 * igual a inexistente. Papel errado é 403.
 */
@Import(AntiIdorTest.RecursosDeTeste.class)
class AntiIdorTest extends IntegracaoTest {

    @RestController
    @RequestMapping("/api/teste/recursos")
    static class RecursosDeTeste {

        record Recurso(UUID id, UUID donoId, String texto) {}

        record NovoRecurso(String texto) {}

        private final Map<UUID, Recurso> recursos = new ConcurrentHashMap<>();
        private final UsuarioAutenticado usuario;

        RecursosDeTeste(UsuarioAutenticado usuario) {
            this.usuario = usuario;
        }

        @PostMapping
        @PreAuthorize("hasRole('CLIENTE')")
        Map<String, UUID> criar(@RequestBody NovoRecurso pedido) {
            Recurso recurso = new Recurso(UUID.randomUUID(), usuario.id(), pedido.texto());
            recursos.put(recurso.id(), recurso);
            return Map.of("id", recurso.id());
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasRole('CLIENTE')")
        Recurso buscar(@PathVariable UUID id) {
            return buscarDoDono(id, usuario.id())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Recurso não encontrado."));
        }

        /** Como um findByIdAndClienteId: a consulta já filtra pelo dono. */
        private Optional<Recurso> buscarDoDono(UUID id, UUID dono) {
            return Optional.ofNullable(recursos.get(id))
                    .filter(recurso -> recurso.donoId().equals(dono));
        }
    }

    private UUID criarRecurso(UUID dono) throws Exception {
        String corpo = mockMvc.perform(post("/api/teste/recursos")
                        .header("Authorization", bearer(dono, Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"meu\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    @Test
    @DisplayName("o dono vê o próprio recurso")
    void donoVe() throws Exception {
        UUID a = UUID.randomUUID();
        UUID recurso = criarRecurso(a);

        mockMvc.perform(get("/api/teste/recursos/" + recurso).header("Authorization", bearer(a, Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.donoId").value(a.toString()));
    }

    @Test
    @DisplayName("B recebe 404 no recurso de A, com a mesma resposta de um recurso inexistente")
    void outroUsuarioRecebe404() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID recurso = criarRecurso(a);

        String deA = mockMvc.perform(
                        get("/api/teste/recursos/" + recurso).header("Authorization", bearer(b, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-encontrado"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String inexistente = mockMvc.perform(get("/api/teste/recursos/" + recurso)
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(deA).isEqualTo(inexistente);
    }

    @Test
    @DisplayName("papel errado: 403 em Problem Details")
    void papelErrado() throws Exception {
        UUID recurso = criarRecurso(UUID.randomUUID());

        mockMvc.perform(get("/api/teste/recursos/" + recurso)
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.PROFISSIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:proibido"));
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() throws Exception {
        mockMvc.perform(get("/api/teste/recursos/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
