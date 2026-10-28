package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.usuario.Papel;

/**
 * Avaliações (DOM-10; RN23, RN55, RF09, RF12): só quem teve diária paga e aprovada pelo app avalia, uma vez por lado
 * em cada contrato; nota de 1 a 5 e comentário opcional sem contato; a média do profissional sai no perfil e na busca.
 */
class AvaliacaoTest extends ExecucaoDeDiariaTeste {

    private ResultActions avaliar(String autorizacao, UUID contratoId, int nota, String comentario) throws Exception {
        String texto = comentario == null ? "" : ",\"comentario\":\"" + comentario + "\"";
        return mockMvc.perform(post("/api/avaliacoes")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contratoId\":\"%s\",\"nota\":%d%s}".formatted(contratoId, nota, texto)));
    }

    private ResultActions situacao(String autorizacao) throws Exception {
        return mockMvc.perform(get("/api/avaliacoes/contratos/" + contrato).header("Authorization", autorizacao));
    }

    private ResultActions perfilPublico() throws Exception {
        return mockMvc.perform(get("/api/publico/profissionais/" + profissional.profissional()));
    }

    /** Pago, trabalhado e aprovado pelo cliente: a diária fica liberada. */
    private void diariaLiberada() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
    }

    private long avaliacoesDoContrato() {
        return jdbc.queryForObject("SELECT count(*) FROM avaliacao WHERE contrato_id = ?", Long.class, contrato);
    }

    @Test
    @DisplayName("cliente avalia o profissional depois da diária liberada; a média sai no perfil sem 'Novo na COE'")
    void clienteAvalia() throws Exception {
        diariaLiberada();
        perfilPublico().andExpect(jsonPath("$.avaliacoes.novoNaCoe").value(true));

        avaliar(doCliente(), contrato, 5, "Caprichou no jardim, tudo limpo no fim.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contratoId").value(contrato.toString()))
                .andExpect(jsonPath("$.nota").value(5))
                .andExpect(jsonPath("$.comentario").value("Caprichou no jardim, tudo limpo no fim."));

        perfilPublico()
                .andExpect(jsonPath("$.avaliacoes.total").value(1))
                .andExpect(jsonPath("$.avaliacoes.nota").value(5.0))
                .andExpect(jsonPath("$.avaliacoes.novoNaCoe").value(false))
                .andExpect(jsonPath("$.avaliacoes.recentes[0].nota").value(5))
                .andExpect(jsonPath("$.avaliacoes.recentes[0].comentario")
                        .value("Caprichou no jardim, tudo limpo no fim."));
        situacao(doCliente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeAvaliar").value(false))
                .andExpect(jsonPath("$.minha.nota").value(5));
    }

    @Test
    @DisplayName("antes de uma diária liberada (só paga, ou aguardando o cliente) ninguém avalia (RN55)")
    void soDepoisDeLiberada() throws Exception {
        situacao(doCliente()).andExpect(jsonPath("$.podeAvaliar").value(false));
        avaliar(doCliente(), contrato, 4, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:avaliacao-indisponivel"));

        pagar();
        trabalharODia(diarias.get(0), dia);
        avaliar(doCliente(), contrato, 4, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:avaliacao-indisponivel"));
        avaliar(doProfissional(), contrato, 4, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:avaliacao-indisponivel"));
        assertThat(avaliacoesDoContrato()).isZero();
    }

    @Test
    @DisplayName("uma avaliação por lado em cada contrato: a segunda é 409")
    void umaPorLado() throws Exception {
        diariaLiberada();
        situacao(doCliente()).andExpect(jsonPath("$.podeAvaliar").value(true));
        avaliar(doCliente(), contrato, 4, null).andExpect(status().isCreated());
        avaliar(doCliente(), contrato, 1, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:avaliacao-ja-feita"));
        assertThat(jdbc.queryForObject(
                        "SELECT nota FROM avaliacao WHERE contrato_id = ? AND autor_id = ?",
                        Integer.class,
                        contrato,
                        cliente))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("profissional avalia o cliente; não mexe na nota do profissional")
    void profissionalAvaliaOCliente() throws Exception {
        diariaLiberada();
        avaliar(doProfissional(), contrato, 3, null).andExpect(status().isCreated());

        assertThat(jdbc.queryForMap(
                        "SELECT avaliado_id, papel_autor FROM avaliacao WHERE contrato_id = ? AND autor_id = ?",
                        contrato,
                        profissional.usuario()))
                .containsEntry("avaliado_id", cliente)
                .containsEntry("papel_autor", "profissional");
        perfilPublico()
                .andExpect(jsonPath("$.avaliacoes.total").value(0))
                .andExpect(jsonPath("$.avaliacoes.novoNaCoe").value(true));
        situacao(doProfissional()).andExpect(jsonPath("$.minha.nota").value(3));
    }

    @Test
    @DisplayName("comentário com contato é recusado e nada é gravado (RN46)")
    void comentarioSemContato() throws Exception {
        diariaLiberada();
        avaliar(doCliente(), contrato, 5, "Muito bom, me liga no 47 99123-4567")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        avaliar(doCliente(), contrato, 5, "x".repeat(501)).andExpect(status().isBadRequest());
        avaliar(doCliente(), contrato, 6, null).andExpect(status().isBadRequest());
        avaliar(doCliente(), contrato, 0, null).andExpect(status().isBadRequest());
        assertThat(avaliacoesDoContrato()).isZero();

        // comentário só com espaços fica sem comentário
        avaliar(doCliente(), contrato, 5, "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comentario").doesNotExist());
    }

    @Test
    @DisplayName("média de todas as avaliações visíveis de clientes; a oculta pelo admin não conta")
    void mediaRecalculada() throws Exception {
        diariaLiberada();
        // outra avaliação visível (nota 4) e uma oculta (nota 1), de outros clientes no mesmo contrato
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        UUID oculto = contas.criarConfirmada(Papel.CLIENTE).id();
        jdbc.update(
                "INSERT INTO avaliacao (contrato_id, autor_id, avaliado_id, papel_autor, nota) VALUES (?, ?, ?, 'cliente', 4)",
                contrato,
                outro,
                profissional.usuario());
        jdbc.update(
                "INSERT INTO avaliacao (contrato_id, autor_id, avaliado_id, papel_autor, nota, visivel)"
                        + " VALUES (?, ?, ?, 'cliente', 1, false)",
                contrato,
                oculto,
                profissional.usuario());

        avaliar(doCliente(), contrato, 5, null).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                        "SELECT nota_media FROM profissional WHERE id = ?",
                        BigDecimal.class,
                        profissional.profissional()))
                .isEqualByComparingTo("4.50");
        perfilPublico().andExpect(jsonPath("$.avaliacoes.total").value(2));
    }

    @Test
    @DisplayName("contrato de outro = 404 igual a inexistente; ADMIN = 403; sem token = 401")
    void antiIdor() throws Exception {
        diariaLiberada();
        UUID estranho = contas.criarConfirmada(Papel.CLIENTE).id();
        String deOutro = avaliar(bearer(estranho, Papel.CLIENTE), contrato, 5, null)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String inexistente = avaliar(bearer(estranho, Papel.CLIENTE), UUID.randomUUID(), 5, null)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(semRequisicao(deOutro)).isEqualTo(semRequisicao(inexistente));
        situacao(bearer(estranho, Papel.CLIENTE)).andExpect(status().isNotFound());

        UUID admin = contas.criarConfirmada(Papel.ADMIN).id();
        avaliar(bearer(admin, Papel.ADMIN), contrato, 5, null).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/avaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contratoId\":\"%s\",\"nota\":5}".formatted(contrato)))
                .andExpect(status().isUnauthorized());
        assertThat(avaliacoesDoContrato()).isZero();
    }
}
