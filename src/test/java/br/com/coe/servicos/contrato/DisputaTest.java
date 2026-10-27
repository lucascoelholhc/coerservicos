package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * Reclamação, decisão do admin e falta do profissional (DOM-09; RN41–RN44e, PA06, PA07). Três quintas-feiras pagas;
 * a reclamação trava só o dia dela; liberar ou reembolsar acontece uma vez só, pelo livro-caixa.
 */
class DisputaTest extends ExecucaoDeDiariaTeste {

    private UUID admin;

    @Override
    protected int quantasDiarias() {
        return 3;
    }

    @BeforeEach
    void pagarEPrepararAdmin() throws Exception {
        pagar();
        admin = contas.criarConfirmada(Papel.ADMIN).id();
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    private ResultActions reclamar(String autorizacao, UUID diaria, String motivo) throws Exception {
        return mockMvc.perform(post("/api/disputas")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diariaId":"%s","motivo":"%s","descricao":"O serviço ficou pela metade."}""".formatted(diaria, motivo)));
    }

    private UUID abrirDisputa(UUID diaria, String motivo) throws Exception {
        String corpo = reclamar(doCliente(), diaria, motivo)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private ResultActions decidir(UUID disputa, String favor) throws Exception {
        return mockMvc.perform(post("/api/admin/disputas/" + disputa + "/decisao")
                .header("Authorization", doAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"favor":"%s","justificativa":"Conferi as fotos e a conversa."}""".formatted(favor)));
    }

    private ResultActions demais(UUID disputa, String acao) throws Exception {
        return mockMvc.perform(post("/api/disputas/" + disputa + "/demais-diarias")
                .header("Authorization", doCliente())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"%s\"}".formatted(acao)));
    }

    /** O que o cliente pagou por uma diária (diária + comissão, a taxa é do cliente no padrão). */
    private BigDecimal pagoPorDia() {
        return totalDoContrato().divide(BigDecimal.valueOf(quantasDiarias()));
    }

    private BigDecimal saldoDoCliente() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE l.natureza WHEN 'C' THEN l.valor ELSE -l.valor END), 0)
                  FROM lancamento l JOIN conta_razao r ON r.id = l.conta_id
                 WHERE r.tipo = 'cliente' AND r.usuario_id = ?""", BigDecimal.class, cliente);
    }

    private int faltas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ocorrencia_profissional WHERE profissional_id = ?",
                Integer.class,
                profissional.profissional());
    }

    @Test
    void reclamacaoTravaSoAquelaDiariaEUmaPorVez() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "qualidade");

        assertThat(statusDa(diarias.get(0))).isEqualTo("contestada");
        assertThat(statusDa(diarias.get(1))).isEqualTo("paga");
        reclamar(doCliente(), diarias.get(0), "qualidade")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/disputas/" + disputa).header("Authorization", doProfissional()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aberta"))
                .andExpect(jsonPath("$.motivo").value("qualidade"))
                .andExpect(jsonPath("$.valorDoDia").value(pagoPorDia().toPlainString()));
    }

    @Test
    void reclamacaoDeOutroOuInvalidaERecusada() throws Exception {
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        reclamar(bearer(outro, Papel.CLIENTE), diarias.get(0), "qualidade").andExpect(status().isNotFound());
        reclamar(doProfissional(), diarias.get(0), "qualidade").andExpect(status().isForbidden());
        reclamar(doCliente(), diarias.get(0), "nao-existe").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/disputas")
                        .header("Authorization", doCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diariaId":"%s","motivo":"outro","descricao":"curta"}""".formatted(diarias.get(0))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/disputas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profissionalRespondeUmaVezEOutroNaoVe() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "qualidade");
        String resposta = "{\"texto\":\"Terminei tudo, mandei as fotos.\"}";
        mockMvc.perform(post("/api/disputas/" + disputa + "/resposta")
                        .header("Authorization", doProfissional())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resposta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("em_analise"))
                .andExpect(jsonPath("$.respostaProfissional").value("Terminei tudo, mandei as fotos."));
        mockMvc.perform(post("/api/disputas/" + disputa + "/resposta")
                        .header("Authorization", doProfissional())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resposta))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:disputa-ja-respondida"));

        ProfissionaisDeTeste.Criado outro = ProfissionaisDeTeste.ativo(contexto);
        mockMvc.perform(get("/api/disputas/" + disputa)
                        .header("Authorization", bearer(outro.usuario(), Papel.PROFISSIONAL)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/disputas/" + disputa).header("Authorization", doCliente()))
                .andExpect(status().isForbidden());
    }

    @Test
    void decisaoAFavorDoProfissionalLiberaPeloLivroCaixa() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "qualidade");
        BigDecimal antes = saldoNoContrato("custodia");

        decidir(disputa, "profissional")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("favor_profissional"));

        assertThat(statusDa(diarias.get(0))).isEqualTo("liberada");
        assertThat(jdbc.queryForObject("SELECT liberada_por FROM diaria WHERE id = ?", String.class, diarias.get(0)))
                .isEqualTo("admin");
        assertThat(antes.subtract(saldoNoContrato("custodia"))).isEqualByComparingTo(pagoPorDia());
        assertThat(saldoDoCliente()).isEqualByComparingTo("0");
        assertThat(faltas()).isZero();
        decidir(disputa, "cliente")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:disputa-decidida"));
        assertThat(saldoDoCliente()).isEqualByComparingTo("0");
    }

    @Test
    void decisaoAFavorDoClienteDevolveExatamenteOQuePagou() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "qualidade");
        BigDecimal antes = saldoNoContrato("custodia");

        decidir(disputa, "cliente")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("favor_cliente"));

        assertThat(statusDa(diarias.get(0))).isEqualTo("reembolsada");
        assertThat(saldoDoCliente()).isEqualByComparingTo(pagoPorDia());
        assertThat(antes.subtract(saldoNoContrato("custodia"))).isEqualByComparingTo(pagoPorDia());
        assertThat(saldoNoContrato("profissional")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                        "SELECT valor_total FROM reembolso WHERE diaria_id = ? AND status = 'pendente'",
                        BigDecimal.class,
                        diarias.get(0)))
                .isEqualByComparingTo(pagoPorDia());
        assertThat(faltas()).as("qualidade não é falta").isZero();
        demais(disputa, "cancelar")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:sem-falta-confirmada"));
    }

    @Test
    void faltaConfirmadaRegistraOcorrenciaEOClienteCancelaAsOutras() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "nao_compareceu");
        decidir(disputa, "cliente").andExpect(status().isOk());

        assertThat(faltas()).isEqualTo(1);
        assertThat(saldoDoCliente()).isEqualByComparingTo(pagoPorDia());
        for (String comando : new String[] {
            "UPDATE ocorrencia_profissional SET criado_em = now() WHERE profissional_id = ?",
            "DELETE FROM ocorrencia_profissional WHERE profissional_id = ?"
        }) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(comando, profissional.profissional()))
                    .as(comando)
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThat(faltas()).isEqualTo(1);
        mockMvc.perform(get("/api/disputas/" + disputa).header("Authorization", doCliente()))
                .andExpect(jsonPath("$.podeEscolherDemais").value(true));

        demais(disputa, "cancelar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demaisDiarias").value("cancelar"));

        assertThat(statusDa(diarias.get(1))).isEqualTo("reembolsada");
        assertThat(statusDa(diarias.get(2))).isEqualTo("reembolsada");
        assertThat(saldoDoCliente()).isEqualByComparingTo(totalDoContrato());
        assertThat(saldoNoContrato("custodia")).isEqualByComparingTo("0");
        demais(disputa, "manter")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:demais-ja-escolhidas"));
    }

    @Test
    void faltaComOClienteMantendoAsOutrasDiarias() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "nao_compareceu");
        decidir(disputa, "cliente").andExpect(status().isOk());

        demais(disputa, "manter").andExpect(status().isOk());

        assertThat(statusDa(diarias.get(1))).isEqualTo("paga");
        assertThat(statusDa(diarias.get(2))).isEqualTo("paga");
        assertThat(saldoDoCliente()).isEqualByComparingTo(pagoPorDia());
    }

    @Test
    void faltaDecididaAFavorDoProfissionalNaoGravaFalta() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "nao_compareceu");
        decidir(disputa, "profissional").andExpect(status().isOk());
        assertThat(faltas()).isZero();
        demais(disputa, "cancelar").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void filaDoAdminEDecisaoSemJustificativa() throws Exception {
        UUID disputa = abrirDisputa(diarias.get(0), "dano");
        String fila = mockMvc.perform(get("/api/admin/disputas").header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(fila).contains(disputa.toString());
        mockMvc.perform(post("/api/admin/disputas/" + disputa + "/decisao")
                        .header("Authorization", doAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favor\":\"cliente\",\"justificativa\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/disputas/" + UUID.randomUUID() + "/decisao")
                        .header("Authorization", doAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favor\":\"cliente\",\"justificativa\":\"Conferi tudo.\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'disputa.abrir' AND entidade_id = ?",
                        Integer.class,
                        disputa))
                .isEqualTo(1);
    }

    @Test
    void diariaAguardandoContestadaNaoLiberaSozinha() throws Exception {
        trabalharODia(diarias.get(0), dia);
        abrirDisputa(diarias.get(0), "servico_incompleto");
        relogio.avancar(java.time.Duration.ofHours(13));
        contexto.getBean(LiberacaoAutomatica.class).executar();
        assertThat(statusDa(diarias.get(0))).isEqualTo("contestada");
    }
}
