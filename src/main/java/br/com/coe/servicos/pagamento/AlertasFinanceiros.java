package br.com.coe.servicos.pagamento;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Alertas do financeiro no painel do admin (PA02): repasse ou estorno com falha, conciliação com diferença, dinheiro
 * recebido sem pedido. O {@code detalhe} leva só dado não pessoal (código do erro, valores).
 */
@Component
public class AlertasFinanceiros {

    /** Tipos de alerta (coluna {@code alerta_financeiro.tipo}). */
    public enum Tipo {
        REPASSE_FALHOU("repasse-falhou"),
        ESTORNO_FALHOU("estorno-falhou"),
        DEVOLUCAO_FALHOU("devolucao-falhou"),
        CONCILIACAO("conciliacao"),
        DINHEIRO_SEM_PEDIDO("dinheiro-sem-pedido"),
        COBRANCA_DESCONHECIDA("cobranca-desconhecida");

        private final String codigo;

        Tipo(String codigo) {
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    AlertasFinanceiros(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Abre o alerta na transação de quem chama (some junto se a ação for desfeita). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void abrir(Tipo tipo, UUID referencia, Map<String, ?> detalhe) {
        jdbc.update(
                """
                INSERT INTO alerta_financeiro (tipo, referencia, detalhe)
                VALUES (:tipo, :referencia, CAST(:detalhe AS jsonb))
                """,
                new MapSqlParameterSource("tipo", tipo.codigo())
                        .addValue("referencia", referencia)
                        .addValue("detalhe", json.writeValueAsString(detalhe)));
    }
}
