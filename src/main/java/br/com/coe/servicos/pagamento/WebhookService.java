package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Webhook do gateway (DOM-07, RN32, RNF06) no formato do Asaas: token no header {@code asaas-access-token} (o Asaas
 * autentica por token, não por HMAC) conferido em tempo constante; evento {@code {id, event, payment{id, value}}} lido
 * de forma tolerante (campo novo do gateway não quebra). Todo evento é guardado com o corpo bruto; só o de token
 * válido ocupa o id ({@code uq_evento_gateway} parcial), então repetido não faz nada e forjado não bloqueia o
 * verdadeiro. O processamento é na mesma transação: ou tudo (status, diárias, contato, livro-caixa) ou nada.
 */
@Service
class WebhookService {

    enum Resultado {
        ACEITO,
        TOKEN_INVALIDO
    }

    private static final int TAMANHO_MAXIMO_DO_ID = 200;
    private static final int TAMANHO_MAXIMO_DO_TIPO = 100;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final GatewayPagamento gateway;
    private final LivroCaixa livro;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;
    private final byte[] resumoDoToken;

    WebhookService(
            NamedParameterJdbcTemplate jdbc,
            JsonMapper json,
            GatewayPagamento gateway,
            LivroCaixa livro,
            RegistroDeAuditoria auditoria,
            Clock clock,
            @Value("${coe.gateway.token-webhook}") String token) {
        this.jdbc = jdbc;
        this.json = json;
        this.gateway = gateway;
        this.livro = livro;
        this.auditoria = auditoria;
        this.clock = clock;
        this.resumoDoToken = resumo(token);
    }

    @Transactional
    Resultado receber(String token, byte[] corpo) {
        JsonNode evento = ler(corpo);
        String id = evento.path("id").asString("");
        String tipo = evento.path("event").asString("");
        if (id.isBlank()
                || tipo.isBlank()
                || id.length() > TAMANHO_MAXIMO_DO_ID
                || tipo.length() > TAMANHO_MAXIMO_DO_TIPO) {
            throw invalido();
        }
        boolean valido = token != null && MessageDigest.isEqual(resumo(token), resumoDoToken);
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UUID> gravado = jdbc.queryForList(
                """
                INSERT INTO evento_gateway (gateway, id_evento, tipo, payload, corpo_bruto, assinatura_valida, recebido_em)
                VALUES (:gateway, :id, :tipo, CAST(:payload AS jsonb), :corpo, :valido, :agora)
                ON CONFLICT (gateway, id_evento) WHERE assinatura_valida DO NOTHING
                RETURNING id
                """,
                new MapSqlParameterSource("gateway", gateway.nome())
                        .addValue("id", id)
                        .addValue("tipo", tipo)
                        .addValue("payload", json.writeValueAsString(evento))
                        .addValue("corpo", corpo)
                        .addValue("valido", valido)
                        .addValue("agora", agora),
                UUID.class);
        if (!valido) {
            return Resultado.TOKEN_INVALIDO;
        }
        if (gravado.isEmpty()) {
            return Resultado.ACEITO; // repetido: já foi processado
        }
        String erro = processar(tipo, evento.path("payment"), agora);
        jdbc.update(
                "UPDATE evento_gateway SET processado_em = :agora, erro = :erro WHERE id = :id",
                new MapSqlParameterSource("agora", agora).addValue("erro", erro).addValue("id", gravado.get(0)));
        return Resultado.ACEITO;
    }

    /** Devolve o motivo de não ter efeito (gravado em {@code evento_gateway.erro}) ou nulo. */
    private String processar(String tipo, JsonNode pagamento, OffsetDateTime agora) {
        String externo = pagamento.path("id").asString("");
        return switch (tipo) {
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" -> confirmar(externo, pagamento.path("value"), agora);
            case "PAYMENT_OVERDUE" -> vencer(externo);
            default -> null; // outros eventos do gateway: guardados, sem efeito
        };
    }

    private String confirmar(String externo, JsonNode valorRecebido, OffsetDateTime agora) {
        List<UUID> contratos = jdbc.queryForList(
                "SELECT contrato_id FROM cobranca WHERE gateway = :gateway AND id_externo = :externo",
                new MapSqlParameterSource("gateway", gateway.nome()).addValue("externo", externo),
                UUID.class);
        if (contratos.isEmpty()) {
            return "cobranca-desconhecida";
        }
        UUID contrato = contratos.get(0);
        // ordem das travas: contrato → cobrança → diárias por data (a mesma da expiração e da liberação)
        Map<String, Object> c = jdbc.queryForMap(
                "SELECT numero, status, cliente_id, profissional_id FROM contrato WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", contrato));
        Map<String, Object> cobranca = jdbc.queryForMap(
                """
                SELECT id, valor, status, metodo FROM cobranca
                 WHERE gateway = :gateway AND id_externo = :externo
                 FOR UPDATE
                """, new MapSqlParameterSource("gateway", gateway.nome()).addValue("externo", externo));
        if ("confirmada".equals(cobranca.get("status"))) {
            return null; // CONFIRMED e depois RECEIVED da mesma cobrança (cartão): o primeiro já valeu
        }
        BigDecimal valor = (BigDecimal) cobranca.get("valor");
        if (!valorRecebido.isNumber() || valorRecebido.decimalValue().compareTo(valor) != 0) {
            return "valor-divergente";
        }
        UUID idCobranca = (UUID) cobranca.get("id");
        String status = (String) c.get("status");
        if (!"aguardando_pagamento".equals(status)) {
            // dinheiro recebido sem pedido esperando: o que fazer com ele é A DEFINIR (a equipe vê pela auditoria)
            String motivo = "pago".equals(status) ? "contrato-ja-pago" : "contrato-" + status;
            auditoria.registrarDoSistema(
                    "pagamento.sem-contrato-pendente",
                    "contrato",
                    contrato,
                    null,
                    Map.of("cobranca", idCobranca.toString(), "motivo", motivo),
                    null);
            return motivo;
        }
        marcarPago(contrato, idCobranca, c, agora);
        Dinheiro pago = new Dinheiro(valor);
        livro.registrar(new LivroCaixa.Transacao(
                "pagamento",
                "pagamento:" + idCobranca,
                contrato,
                null,
                idCobranca,
                "Pagamento do pedido nº " + ((Number) c.get("numero")).longValue(),
                List.of(
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.DEBITO, pago),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.CUSTODIA, LivroCaixa.Natureza.CREDITO, pago))));
        auditoria.registrarDoSistema(
                "pagamento.confirmado",
                "contrato",
                contrato,
                Map.of("status", status),
                Map.of("status", "pago", "metodo", cobranca.get("metodo"), "valor", valor.toPlainString()),
                null);
        return null;
    }

    private void marcarPago(UUID contrato, UUID cobranca, Map<String, Object> c, OffsetDateTime agora) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("contrato", contrato)
                .addValue("cobranca", cobranca)
                .addValue("agora", agora)
                .addValue("cliente", c.get("cliente_id"))
                .addValue("profissional", c.get("profissional_id"));
        jdbc.update(
                "UPDATE cobranca SET status = 'confirmada', confirmada_em = :agora WHERE id = :cobranca", parametros);
        jdbc.update("""
                UPDATE contrato SET status = 'pago', pago_em = :agora, contato_liberado_em = :agora
                 WHERE id = :contrato
                """, parametros);
        jdbc.queryForList(
                "SELECT id FROM diaria WHERE contrato_id = :contrato ORDER BY data FOR UPDATE", parametros, UUID.class);
        jdbc.update("""
                WITH pagas AS (
                    UPDATE diaria SET status = 'paga', atualizado_em = :agora
                     WHERE contrato_id = :contrato AND status = 'agendada'
                    RETURNING id
                )
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, criado_em)
                SELECT id, 'agendada', 'paga', 'gateway', :agora FROM pagas
                """, parametros);
        // RN50: com o contrato pago, a conversa dos dois deixa de ser censurada
        jdbc.update("""
                UPDATE conversa SET contato_liberado_em = :agora
                 WHERE cliente_id = :cliente AND profissional_id = :profissional AND contato_liberado_em IS NULL
                """, parametros);
    }

    private String vencer(String externo) {
        int vencidas =
                jdbc.update("""
                UPDATE cobranca SET status = 'expirada'
                 WHERE gateway = :gateway AND id_externo = :externo AND status = 'pendente'
                """, new MapSqlParameterSource("gateway", gateway.nome()).addValue("externo", externo));
        return vencidas == 0 ? "cobranca-desconhecida" : null;
    }

    private JsonNode ler(byte[] corpo) {
        JsonNode evento;
        try {
            evento = json.readTree(corpo);
        } catch (JacksonException erro) {
            throw invalido();
        }
        if (evento == null || !evento.isObject()) {
            throw invalido();
        }
        return evento;
    }

    private static ErrorResponseException invalido() {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "O evento do gateway não pôde ser lido.");
        problema.setType(URI.create("urn:coe:erro:evento-invalido"));
        problema.setTitle("Evento inválido");
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problema, null);
    }

    private static byte[] resumo(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(erro);
        }
    }
}
