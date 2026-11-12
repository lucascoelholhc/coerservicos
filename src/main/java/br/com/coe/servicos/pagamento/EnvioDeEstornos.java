package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Envio dos estornos (PA02, RN34b/RN34c), uma instância por vez (ShedLock): o da diária reembolsada (parcial, no valor
 * exato do que o cliente pagou por ela) e a devolução integral do dinheiro recebido sem pedido. O gateway não deixa
 * consultar estorno por referência, então a linha é marcada {@code enviado} antes da chamada (numa transação curta): uma
 * queda no meio deixa o estorno parado e visível ao admin, nunca o dinheiro devolvido duas vezes. Erro na chamada =
 * falhou, com alerta no painel.
 */
@Component
public class EnvioDeEstornos {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeEstornos.class);
    private static final int LOTE = 50;

    private record Pedido(UUID id, String cobrancaExterna, Dinheiro valor, String referencia) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final GatewayPagamento gateway;
    private final Estornos estornos;
    private final Clock clock;

    EnvioDeEstornos(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transacao,
            GatewayPagamento gateway,
            Estornos estornos,
            Clock clock) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.gateway = gateway;
        this.estornos = estornos;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${coe.pagamento.envio-estornos:1m}",
            initialDelayString = "${coe.pagamento.envio-estornos:1m}")
    @SchedulerLock(name = "envio-de-estornos", lockAtMostFor = "PT10M")
    public void executar() {
        for (Estornos.Destino destino : Estornos.Destino.values()) {
            List<UUID> pendentes = jdbc.queryForList(
                    "SELECT id FROM " + destino.tabela + " WHERE status = 'pendente' ORDER BY criado_em LIMIT :lote",
                    new MapSqlParameterSource("lote", LOTE),
                    UUID.class);
            for (UUID id : pendentes) {
                try {
                    enviarUm(destino, id);
                } catch (RuntimeException erro) {
                    LOG.error(
                            "Estorno {} ({}) não enviado: {}",
                            id,
                            destino,
                            erro.getClass().getSimpleName());
                }
            }
        }
    }

    private void enviarUm(Estornos.Destino destino, UUID id) {
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Pedido pedido = transacao.execute(status -> reservar(destino, id, agora));
        if (pedido == null) {
            return; // outra rodada já pegou
        }
        GatewayPagamento.EstornoNoGateway estorno;
        try {
            estorno = gateway.estornar(pedido.cobrancaExterna(), pedido.valor(), pedido.referencia());
        } catch (RuntimeException erro) {
            // sem resposta não quer dizer que não saiu: fica enviado (incerto), com alerta; o webhook ainda confirma
            transacao.executeWithoutResult(status -> estornos.incerto(destino, id));
            throw erro;
        }
        transacao.executeWithoutResult(status -> gravar(destino, id, estorno, agora));
    }

    /** Marca enviado antes de chamar o gateway (sem consulta por referência, é o que evita estornar duas vezes). */
    private Pedido reservar(Estornos.Destino destino, UUID id, OffsetDateTime agora) {
        String valor = destino == Estornos.Destino.REEMBOLSO ? "x.valor_total" : "x.valor";
        String referencia =
                destino == Estornos.Destino.REEMBOLSO ? "'reembolso:' || x.diaria_id" : "'devolucao:' || x.id";
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "UPDATE " + destino.tabela
                        + " x SET status = 'enviado', enviado_em = :agora, tentativas = tentativas + 1"
                        + " FROM cobranca c WHERE x.id = :id AND x.status = 'pendente' AND c.id = x.cobranca_id"
                        + " RETURNING c.id_externo, " + valor + " AS valor, " + referencia + " AS referencia",
                new MapSqlParameterSource("id", id).addValue("agora", agora));
        if (linhas.isEmpty()) {
            return null;
        }
        Map<String, Object> linha = linhas.get(0);
        return new Pedido(id, (String) linha.get("id_externo"), new Dinheiro((BigDecimal) linha.get("valor")), (String)
                linha.get("referencia"));
    }

    private void gravar(
            Estornos.Destino destino, UUID id, GatewayPagamento.EstornoNoGateway estorno, OffsetDateTime agora) {
        String situacao = jdbc.queryForObject(
                "SELECT status FROM " + destino.tabela + " WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id),
                String.class);
        if (!"enviado".equals(situacao)) {
            return;
        }
        jdbc.update(
                "UPDATE " + destino.tabela + " SET id_externo = :externo WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("externo", estorno.idExterno()));
        if ("DONE".equals(estorno.status())) {
            confirmar(destino, id, agora);
        } else if ("FAILED".equals(estorno.status())) {
            estornos.falhar(destino, id, "gateway-recusou");
        }
    }

    private void confirmar(Estornos.Destino destino, UUID id, OffsetDateTime agora) {
        if (destino == Estornos.Destino.REEMBOLSO) {
            estornos.confirmarReembolso(id, agora);
        } else {
            estornos.confirmarDevolucao(id, agora);
        }
    }
}
