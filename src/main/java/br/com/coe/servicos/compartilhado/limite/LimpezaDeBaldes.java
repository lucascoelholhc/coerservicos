package br.com.coe.servicos.compartilhado.limite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga os baldes vencidos da limite_balde, em lotes. O DELETE usa SKIP LOCKED: várias instâncias podem
 * rodar juntas sem ShedLock e sem esperar a trava de um balde em uso.
 */
@Component
public class LimpezaDeBaldes {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaDeBaldes.class);
    private static final int LOTE = 500;
    private static final int MAXIMO_DE_LOTES = 200;

    private final LimitadorDeTaxa limitador;

    LimpezaDeBaldes(LimitadorDeTaxa limitador) {
        this.limitador = limitador;
    }

    @Scheduled(fixedDelayString = "${coe.limites.limpeza:1h}", initialDelayString = "${coe.limites.limpeza:1h}")
    public void executar() {
        int apagados = 0;
        for (int lote = 0; lote < MAXIMO_DE_LOTES; lote++) {
            int doLote = limitador.removerVencidos(LOTE);
            apagados += doLote;
            if (doLote < LOTE) {
                break;
            }
        }
        LOG.info("Baldes vencidos apagados: {}", apagados);
    }
}
