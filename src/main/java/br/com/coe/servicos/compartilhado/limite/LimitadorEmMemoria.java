package br.com.coe.servicos.compartilhado.limite;

import java.time.Clock;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;

/**
 * Baldes na memória desta instância (CORE-07): teto geral por IP em /api/** e GETs públicos. São os de
 * maior volume e não protegem segredo nem custo de envio: o limite por instância basta e não custa uma
 * ida ao banco. Cache com teto de chaves e expiração por acesso (não cresce sem fim numa inundação).
 */
public class LimitadorEmMemoria {

    private static final String MENSAGEM = "Muitas tentativas. Aguarde um pouco e tente de novo.";
    private static final long MAXIMO_DE_CHAVES = 100_000;

    private final PropriedadesDeLimites propriedades;
    private final RelogioDoBucket4j relogio;
    private final Cache<String, Bucket> baldes;

    public LimitadorEmMemoria(PropriedadesDeLimites propriedades, Clock clock) {
        this.propriedades = propriedades;
        this.relogio = new RelogioDoBucket4j(clock);
        Duration maiorJanela = propriedades.regras().values().stream()
                .map(PropriedadesDeLimites.Regra::janela)
                .max(Duration::compareTo)
                .orElse(Duration.ofMinutes(1));
        this.baldes = Caffeine.newBuilder()
                .maximumSize(MAXIMO_DE_CHAVES)
                .expireAfterAccess(maiorJanela)
                .build();
    }

    /** Cobra um pedido do balde; sem saldo, 429 com a espera. */
    public void consumir(Balde balde) {
        PropriedadesDeLimites.Regra regra = propriedades.regra(balde.regra());
        Bucket bucket = baldes.get(balde.regra() + ":" + balde.chave(), chave -> novo(regra));
        ConsumptionProbe resultado = bucket.tryConsumeAndReturnRemaining(1);
        if (!resultado.isConsumed()) {
            throw new MuitasTentativasException(MENSAGEM, Duration.ofNanos(resultado.getNanosToWaitForRefill()));
        }
    }

    private Bucket novo(PropriedadesDeLimites.Regra regra) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(regra.capacidade())
                        .refillIntervally(regra.capacidade(), regra.janela())
                        .build())
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Só para os testes: esquece todos os baldes. */
    public void limpar() {
        baldes.invalidateAll();
    }
}
