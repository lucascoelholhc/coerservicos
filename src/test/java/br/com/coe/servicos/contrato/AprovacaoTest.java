package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.compartilhado.dinheiro.ValoresDiaria;
import br.com.coe.servicos.usuario.Papel;

/**
 * DOM-08 (dia 31): o cliente aprova o dia (RN37) e o valor daquela diária sai da custódia do contrato: o repasse vai
 * para a conta do profissional e a comissão vira receita da COE (RN33, RN34), numa transação balanceada e uma vez só
 * (RN39). Os totais batem centavo por centavo: custódia + profissional + receita = total pago.
 */
class AprovacaoTest extends ExecucaoDeDiariaTeste {

    @Override
    protected int quantasDiarias() {
        return 2;
    }

    /** Os valores da diária com a comissão e a taxa congeladas no contrato. */
    private ValoresDiaria valoresDa(UUID diaria) {
        Map<String, Object> linha = jdbc.queryForMap("""
                SELECT d.valor, c.comissao_pct, c.taxa_paga_por FROM diaria d JOIN contrato c ON c.id = d.contrato_id
                 WHERE d.id = ?
                """, diaria);
        return CalculadoraDiaria.calcular(
                new Dinheiro((BigDecimal) linha.get("valor")),
                new Percentual((BigDecimal) linha.get("comissao_pct")),
                TaxaPagaPor.deCodigo((String) linha.get("taxa_paga_por")));
    }

    @Test
    @DisplayName(
            "cliente aprova: diária liberada por ele, histórico, e o livro-caixa tira da custódia para o profissional")
    void aprova() throws Exception {
        pagar();
        UUID diaria = diarias.get(0);
        trabalharODia(diaria, dia);

        aprovar(doCliente(), diaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("liberada"))
                .andExpect(jsonPath("$.liberadaPor").value("cliente"));

        Map<String, Object> linha =
                jdbc.queryForMap("SELECT liberada_por, liberada_em, aprovada_em FROM diaria WHERE id = ?", diaria);
        assertThat(linha.get("liberada_por")).isEqualTo("cliente");
        assertThat(linha.get("liberada_em")).isNotNull();
        assertThat(linha.get("aprovada_em")).isNotNull();
        assertThat(jdbc.queryForList(
                        "SELECT de_status || '>' || para_status || '/' || origem FROM historico_diaria"
                                + " WHERE diaria_id = ? ORDER BY id",
                        String.class,
                        diaria))
                .endsWith("aguardando>liberada/cliente");

        ValoresDiaria valores = valoresDa(diaria);
        List<Map<String, Object>> lancamentos = jdbc.queryForList("""
                SELECT r.tipo, l.natureza, l.valor FROM lancamento l
                  JOIN transacao_financeira t ON t.id = l.transacao_id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.diaria_id = ? AND t.tipo = 'liberacao'
                 ORDER BY r.tipo
                """, diaria);
        assertThat(lancamentos).hasSize(3);
        assertThat(lancamentos)
                .anySatisfy(l -> {
                    assertThat(l.get("tipo")).isEqualTo("custodia");
                    assertThat(l.get("natureza")).isEqualTo("D");
                    assertThat((BigDecimal) l.get("valor"))
                            .isEqualByComparingTo(valores.totalCliente().valor());
                })
                .anySatisfy(l -> {
                    assertThat(l.get("tipo")).isEqualTo("profissional");
                    assertThat(l.get("natureza")).isEqualTo("C");
                    assertThat((BigDecimal) l.get("valor"))
                            .isEqualByComparingTo(valores.repasseProfissional().valor());
                })
                .anySatisfy(l -> {
                    assertThat(l.get("tipo")).isEqualTo("receita_coe");
                    assertThat(l.get("natureza")).isEqualTo("C");
                    assertThat((BigDecimal) l.get("valor"))
                            .isEqualByComparingTo(valores.comissao().valor());
                });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE acao = 'diaria.liberada' AND entidade_id = ?",
                        Integer.class,
                        diaria))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("as duas diárias aprovadas: custódia do contrato zera e profissional + receita = total pago")
    void totaisBatem() throws Exception {
        pagar();
        trabalharODia(diarias.get(0), dia);
        aprovar(doCliente(), diarias.get(0)).andExpect(status().isOk());
        trabalharODia(diarias.get(1), dia.plusWeeks(1));
        aprovar(doCliente(), diarias.get(1)).andExpect(status().isOk());

        BigDecimal total = totalDoContrato();
        assertThat(saldoNoContrato("custodia")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldoNoContrato("profissional").add(saldoNoContrato("receita_coe")))
                .isEqualByComparingTo(total);
        assertThat(saldoNoContrato("gateway")).isEqualByComparingTo(total.negate());
    }

    @Test
    @DisplayName("aprovar de novo, aprovar antes de terminar ou diária contestada: recusado, sem dinheiro movido")
    void recusados() throws Exception {
        pagar();
        UUID diaria = diarias.get(0);
        aprovar(doCliente(), diaria)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));

        trabalharODia(diaria, dia);
        aprovar(doCliente(), diaria).andExpect(status().isOk());
        aprovar(doCliente(), diaria)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM transacao_financeira WHERE diaria_id = ? AND tipo = 'liberacao'",
                        Integer.class,
                        diaria))
                .isEqualTo(1);

        UUID outra = diarias.get(1);
        jdbc.update("UPDATE diaria SET status = 'contestada' WHERE id = ?", outra);
        aprovar(doCliente(), outra)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:diaria-contestada"));
    }

    @Test
    @DisplayName("diária de outro cliente = 404 igual a inexistente; profissional = 403; sem token = 401")
    void antiIdor() throws Exception {
        pagar();
        UUID diaria = diarias.get(0);
        trabalharODia(diaria, dia);
        String outro = bearer(contas.criarConfirmada(Papel.CLIENTE).id(), Papel.CLIENTE);

        String alheia = aprovar(outro, diaria)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String inexistente = aprovar(outro, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(semRequisicao(alheia).replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(semRequisicao(inexistente).replaceAll("\"instance\":\"[^\"]*\"", ""));
        aprovar(doProfissional(), diaria).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/diarias/" + diaria + "/aprovar")).andExpect(status().isUnauthorized());
        assertThat(statusDa(diaria)).isEqualTo("aguardando");
    }

    @Test
    @DisplayName("custódia do contrato sem dinheiro (contrato não pago): nunca usa o dinheiro de outro contrato")
    void semCustodia() throws Exception {
        UUID diaria = diarias.get(0);
        // estado impossível pela API (diária aguardando num contrato não pago), montado direto no banco
        jdbc.update("""
                UPDATE diaria SET status = 'aguardando', chegou_em = now(), terminou_em = now(), auto_libera_em = now()
                 WHERE id = ?
                """, diaria);

        aprovar(doCliente(), diaria).andExpect(status().isInternalServerError());

        assertThat(statusDa(diaria)).isEqualTo("aguardando");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM transacao_financeira WHERE diaria_id = ?", Integer.class, diaria))
                .isZero();
    }
}
