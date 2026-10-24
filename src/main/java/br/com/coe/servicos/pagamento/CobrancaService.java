package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Cobrança do contrato (DOM-07, RN30/RN32): o cliente escolhe Pix ou cartão e recebe a cobrança do gateway. O valor
 * é sempre o total do contrato no banco; o prazo é o do contrato. Pedir de novo com o mesmo método devolve a cobrança
 * pendente; outro método cria outra (a primeira confirmada vence, {@code uq_cobranca_confirmada}). O contrato só vira
 * pago pelo webhook do gateway, nunca por esta chamada.
 */
@Service
class CobrancaService {

    private static final String NAO_ENCONTRADO = "Contrato não encontrado.";

    private static final String COLUNAS = "id, metodo, status, valor, pix_copia_cola, link_pagamento, expira_em";

    private static final RowMapper<CobrancaDtos.Pagamento> PAGAMENTO = (linha, n) -> new CobrancaDtos.Pagamento(
            linha.getObject("id", UUID.class),
            linha.getString("metodo"),
            linha.getString("status"),
            new Dinheiro(linha.getBigDecimal("valor")),
            linha.getString("pix_copia_cola"),
            linha.getString("link_pagamento"),
            linha.getTimestamp("expira_em") == null
                    ? null
                    : linha.getTimestamp("expira_em").toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final GatewayPagamento gateway;
    private final Clock clock;

    CobrancaService(
            NamedParameterJdbcTemplate jdbc, UsuarioAutenticado usuario, GatewayPagamento gateway, Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    CobrancaDtos.Resultado pagar(UUID contrato, GatewayPagamento.Metodo metodo) {
        // trava o contrato: dois pedidos ao mesmo tempo não criam duas cobranças do mesmo método
        List<Map<String, Object>> linhas =
                jdbc.queryForList("""
                SELECT numero, status, valor_total, pagamento_expira_em FROM contrato
                 WHERE id = :id AND cliente_id = :eu
                 FOR UPDATE
                """, new MapSqlParameterSource("id", contrato).addValue("eu", usuario.id()));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        Map<String, Object> c = linhas.get(0);
        Instant expira = ((Timestamp) c.get("pagamento_expira_em")).toInstant();
        if (!"aguardando_pagamento".equals(c.get("status")) || !expira.isAfter(clock.instant())) {
            throw new RegraDeNegocioException(
                    "contrato-sem-pagamento-pendente", "Esse pedido não está mais esperando pagamento.");
        }
        List<CobrancaDtos.Pagamento> pendente = jdbc.query(
                "SELECT " + COLUNAS + " FROM cobranca"
                        + " WHERE contrato_id = :contrato AND metodo = :metodo AND status = 'pendente'"
                        + " ORDER BY criado_em DESC, id LIMIT 1",
                new MapSqlParameterSource("contrato", contrato).addValue("metodo", metodo.codigo()),
                PAGAMENTO);
        if (!pendente.isEmpty()) {
            return new CobrancaDtos.Resultado(pendente.get(0), false);
        }

        UUID id = UUID.randomUUID();
        Dinheiro valor = new Dinheiro((BigDecimal) c.get("valor_total"));
        GatewayPagamento.CobrancaNoGateway criada = gateway.criarCobranca(new GatewayPagamento.NovaCobranca(
                id, valor, metodo, expira, "COE - pedido nº " + ((Number) c.get("numero")).longValue()));
        jdbc.update(
                """
                INSERT INTO cobranca (id, contrato_id, gateway, id_externo, metodo, valor, status, pix_copia_cola,
                                      link_pagamento, expira_em)
                VALUES (:id, :contrato, :gateway, :externo, :metodo, :valor, 'pendente', :pix, :link, :expira)
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("contrato", contrato)
                        .addValue("gateway", gateway.nome())
                        .addValue("externo", criada.idExterno())
                        .addValue("metodo", metodo.codigo())
                        .addValue("valor", valor.valor())
                        .addValue("pix", criada.pixCopiaECola())
                        .addValue("link", criada.linkPagamento())
                        .addValue("expira", OffsetDateTime.ofInstant(expira, ZoneOffset.UTC)));
        return new CobrancaDtos.Resultado(
                new CobrancaDtos.Pagamento(
                        id, metodo.codigo(), "pendente", valor, criada.pixCopiaECola(), criada.linkPagamento(), expira),
                true);
    }

    /** A cobrança mais recente do contrato (só o cliente dono). */
    @Transactional(readOnly = true)
    CobrancaDtos.Pagamento ver(UUID contrato) {
        Boolean meu = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM contrato WHERE id = :id AND cliente_id = :eu)",
                new MapSqlParameterSource("id", contrato).addValue("eu", usuario.id()),
                Boolean.class);
        if (!Boolean.TRUE.equals(meu)) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        return jdbc
                .query(
                        "SELECT " + COLUNAS + " FROM cobranca WHERE contrato_id = :contrato"
                                + " ORDER BY criado_em DESC, id LIMIT 1",
                        new MapSqlParameterSource("contrato", contrato),
                        PAGAMENTO)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esse pedido ainda não tem pagamento."));
    }
}
