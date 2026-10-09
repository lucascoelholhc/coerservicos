package br.com.coe.servicos.catalogo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Catálogo público guardado por 60 s (contados pelo {@link Clock}), no mesmo modelo do
 * ConfiguracaoNegocio: se a releitura falhar, mantém o último catálogo válido e só tenta de novo no
 * próximo prazo; {@link #invalidarCache()} força a releitura (admin, no futuro). A primeira leitura
 * acontece no primeiro pedido, não na subida.
 */
@Service
public class CatalogoPublico {

    static final Duration VALIDADE_DO_CACHE = Duration.ofSeconds(60);
    private static final Logger LOG = LoggerFactory.getLogger(CatalogoPublico.class);

    /** Sem catálogo guardado e com a primeira leitura falhando, repete a falha por este tempo sem ir ao banco. */
    static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(5);

    private record Snapshot(CatalogoResposta catalogo, Instant lidoEm, long geracao) {}

    private record Falha(RuntimeException erro, Instant em, long geracao) {}

    private final FonteCatalogo fonte;
    private final Clock clock;
    private final Object trava = new Object();
    private final AtomicLong geracao = new AtomicLong();
    private volatile Snapshot guardado;
    private volatile Falha falhaRecente;

    CatalogoPublico(FonteCatalogo fonte, Clock clock) {
        this.fonte = fonte;
        this.clock = clock;
    }

    /** O catálogo em vigor (do cache, se ainda valer). */
    public CatalogoResposta atual() {
        Snapshot snapshot = guardado;
        if (valido(snapshot, clock.instant())) {
            return snapshot.catalogo();
        }
        synchronized (trava) {
            Instant agora = clock.instant();
            snapshot = guardado;
            if (valido(snapshot, agora)) {
                return snapshot.catalogo();
            }
            long geracaoDaLeitura = geracao.get();
            Falha falha = falhaRecente;
            if (snapshot == null && recente(falha, agora)) {
                // Banco fora na primeira leitura: não insiste a cada pedido (endpoint público, sem login).
                throw falha.erro();
            }
            try {
                guardado = new Snapshot(fonte.ler(), agora, geracaoDaLeitura);
                falhaRecente = null;
            } catch (RuntimeException erro) {
                if (snapshot == null) {
                    falhaRecente = new Falha(erro, agora, geracaoDaLeitura);
                    throw erro;
                }
                LOG.error("Não foi possível reler o catálogo; mantendo o último válido", erro);
                guardado = new Snapshot(snapshot.catalogo(), agora, geracaoDaLeitura);
            }
            return guardado.catalogo();
        }
    }

    /** Descarta o catálogo guardado: a próxima leitura vai ao banco. */
    public void invalidarCache() {
        geracao.incrementAndGet();
    }

    private boolean recente(Falha falha, Instant agora) {
        return falha != null
                && falha.geracao() == geracao.get()
                && !agora.isBefore(falha.em())
                && agora.isBefore(falha.em().plus(ESPERA_APOS_FALHA));
    }

    private boolean valido(Snapshot snapshot, Instant agora) {
        return snapshot != null
                && snapshot.geracao() == geracao.get()
                && !agora.isBefore(snapshot.lidoEm())
                && agora.isBefore(snapshot.lidoEm().plus(VALIDADE_DO_CACHE));
    }
}
