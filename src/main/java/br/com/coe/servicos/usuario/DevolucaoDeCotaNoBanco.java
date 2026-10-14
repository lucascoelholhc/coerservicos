package br.com.coe.servicos.usuario;

import java.sql.Timestamp;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.mensageria.DevolucaoDeCota;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;

/**
 * Mensagem descartada pela fila de envio cheia (CORE-07): marca {@code descartado_em} na linha que a
 * gerou, que sai das contagens da espera de 60 s e do teto por hora (a cota volta). Só aquela linha,
 * pelo id; não sobrescreve uma invalidação que já existia (código substituído por um mais novo); não
 * toca código ou link já usado; descarte já marcado fica como está (M3/L5 da revisão da V16). Roda
 * depois do commit da requisição, numa transação nova.
 */
@Component
class DevolucaoDeCotaNoBanco implements DevolucaoDeCota {

    private static final Logger LOG = LoggerFactory.getLogger(DevolucaoDeCotaNoBanco.class);
    // greatest(): um relógio atrasado nunca deixa invalidado_em antes de criado_em (CHECK da V14)
    private static final String DESCARTAR = " SET descartado_em = ?,"
            + " invalidado_em = coalesce(invalidado_em, greatest(?, criado_em))"
            + " WHERE id = ? AND usado_em IS NULL AND descartado_em IS NULL";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock clock;

    DevolucaoDeCotaNoBanco(JdbcTemplate jdbc, PlatformTransactionManager transacoes, Clock clock) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(transacoes);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public void devolver(MensagemPronta.Cota cota) {
        String tabela = switch (cota.origem()) {
            case CODIGO_SMS -> "codigo_sms";
            case TOKEN_VERIFICACAO -> "token_verificacao";
        };
        Timestamp agora = Timestamp.from(clock.instant());
        Integer linhas =
                transacao.execute(status -> jdbc.update("UPDATE " + tabela + DESCARTAR, agora, agora, cota.id()));
        if (linhas != null && linhas > 0) {
            LOG.info("Cota devolvida ({}): {}", cota.origem(), cota.id());
        } else {
            LOG.info("Cota não devolvida, linha já usada ou já descartada ({}): {}", cota.origem(), cota.id());
        }
    }
}
