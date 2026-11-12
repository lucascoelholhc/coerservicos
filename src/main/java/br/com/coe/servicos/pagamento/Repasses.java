package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Repasse por Pix ao profissional na conta única (PA02, RN34a): um por diária liberada. Nasce pendente na transação
 * da liberação; o {@link EnvioDeRepasses} manda o Pix (só com a chave conferida pelo admin, RN15); a confirmação do
 * gateway lança no livro-caixa (D profissional, C gateway, chave {@code repasse:<diária>}); a falha abre um alerta no
 * painel e espera a nova tentativa manual do admin. A chave Pix só é decifrada no envio.
 */
@Component
public class Repasses {

    private final NamedParameterJdbcTemplate jdbc;
    private final LivroCaixa livro;
    private final AlertasFinanceiros alertas;
    private final RegistroDeAuditoria auditoria;
    private final Estornos estornos;

    Repasses(
            NamedParameterJdbcTemplate jdbc,
            LivroCaixa livro,
            AlertasFinanceiros alertas,
            RegistroDeAuditoria auditoria,
            Estornos estornos) {
        this.jdbc = jdbc;
        this.livro = livro;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.estornos = estornos;
    }

    /**
     * Na liberação (mesma transação): o repasse pendente da diária, com o valor do repasse. A chave copiada aqui só
     * marca o lugar (cifrada, sem decifrar); o envio copia a chave vigente do profissional.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void agendar(UUID diaria, UUID profissional, Dinheiro valor) {
        UUID repasse = jdbc.queryForObject(
                """
                INSERT INTO repasse (profissional_id, valor, chave_pix_tipo, chave_pix_cifrada)
                SELECT id, :valor, chave_pix_tipo, chave_pix_cifrada FROM profissional WHERE id = :profissional
                RETURNING id
                """,
                new MapSqlParameterSource("profissional", profissional).addValue("valor", valor.valor()),
                UUID.class);
        jdbc.update(
                "INSERT INTO repasse_diaria (repasse_id, diaria_id) VALUES (:repasse, :diaria)",
                new MapSqlParameterSource("repasse", repasse).addValue("diaria", diaria));
    }

    /**
     * O gateway concluiu a transferência: livro-caixa e status, uma vez só. Quem chama já travou a linha (envio ou
     * webhook) e conferiu que ela está enviada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void confirmar(UUID repasse, OffsetDateTime agora) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT r.profissional_id, r.valor, rd.diaria_id, d.contrato_id
                  FROM repasse r
                  JOIN repasse_diaria rd ON rd.repasse_id = r.id
                  JOIN diaria d ON d.id = rd.diaria_id
                 WHERE r.id = :id
                """, new MapSqlParameterSource("id", repasse));
        UUID diaria = (UUID) r.get("diaria_id");
        Dinheiro valor = new Dinheiro((BigDecimal) r.get("valor"));
        livro.registrar(new LivroCaixa.Transacao(
                "repasse",
                "repasse:" + diaria,
                (UUID) r.get("contrato_id"),
                diaria,
                null,
                "Repasse por Pix da diária",
                List.of(
                        LivroCaixa.Lancamento.paraProfissional(
                                (UUID) r.get("profissional_id"), LivroCaixa.Natureza.DEBITO, valor),
                        new LivroCaixa.Lancamento(LivroCaixa.Conta.GATEWAY, LivroCaixa.Natureza.CREDITO, valor))));
        UUID transacao = jdbc.queryForObject(
                "SELECT id FROM transacao_financeira WHERE chave_idempotencia = :chave",
                new MapSqlParameterSource("chave", "repasse:" + diaria),
                UUID.class);
        estornos.taxaDaTransferencia((UUID) r.get("contrato_id"), diaria);
        jdbc.update(
                """
                UPDATE repasse SET status = 'confirmado', confirmado_em = :agora, transacao_id = :transacao, erro = NULL
                 WHERE id = :id
                """,
                new MapSqlParameterSource("id", repasse)
                        .addValue("agora", agora)
                        .addValue("transacao", transacao));
        auditoria.registrarDoSistema(
                "repasse.confirmado",
                "repasse",
                repasse,
                Map.of("status", "enviado"),
                Map.of("status", "confirmado", "valor", valor.valor().toPlainString()),
                null);
    }

    /** O gateway recusou: status falhou com o código do motivo, alerta no painel e auditoria. */
    @Transactional(propagation = Propagation.MANDATORY)
    void falhar(UUID repasse, String motivoDoGateway) {
        String motivo = motivo(motivoDoGateway);
        jdbc.update(
                "UPDATE repasse SET status = 'falhou', erro = :erro WHERE id = :id",
                new MapSqlParameterSource("id", repasse).addValue("erro", motivo));
        alertas.abrir(AlertasFinanceiros.Tipo.REPASSE_FALHOU, repasse, Map.of("motivo", motivo));
        auditoria.registrarDoSistema(
                "repasse.falhou", "repasse", repasse, Map.of("status", "enviado"), Map.of("motivo", motivo), null);
    }

    /** O motivo do gateway vira um código nosso (sem dado pessoal): chave inválida, titular diferente ou outro. */
    static String motivo(String doGateway) {
        String texto = doGateway == null ? "" : doGateway.toUpperCase(Locale.ROOT);
        if (texto.contains("KEY")) {
            return "chave-invalida";
        }
        if (texto.contains("HOLDER") || texto.contains("OWNER") || texto.contains("NAME")) {
            return "titular-diferente";
        }
        return "outro";
    }
}
