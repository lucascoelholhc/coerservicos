package br.com.coe.servicos.compartilhado.limite;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de taxa (CORE-07), em {@code coe.limites}. Técnicos, por perfil no application.yml (não na
 * tabela configuracao). Configuração ruim impede a subida.
 *
 * @param lockTimeout espera máxima pela trava de um balde no Postgres (SET LOCAL, só na transação do
 *     limitador); estourou = 429 com {@code esperaNaDisputa}
 * @param esperaNaDisputa o Retry-After quando a trava está disputada
 * @param conexoes tamanho do pool próprio do limitador (não esgota o pool da aplicação)
 * @param esperaPorConexao espera máxima por uma conexão desse pool; estourou = 503
 * @param regras regras por nome
 */
@ConfigurationProperties("coe.limites")
public record PropriedadesDeLimites(
        @DefaultValue("200ms") Duration lockTimeout,
        @DefaultValue("1s") Duration esperaNaDisputa,
        @DefaultValue("5") int conexoes,
        @DefaultValue("500ms") Duration esperaPorConexao,
        Map<String, Regra> regras) {

    public PropriedadesDeLimites {
        if (lockTimeout.isNegative()
                || lockTimeout.isZero()
                || esperaNaDisputa.isNegative()
                || esperaNaDisputa.isZero()
                || conexoes < 1
                || esperaPorConexao.isNegative()
                || esperaPorConexao.isZero()) {
            throw new IllegalStateException("coe.limites inválido: tempos positivos e conexoes >= 1");
        }
        regras = regras == null ? Map.of() : Map.copyOf(regras);
    }

    Regra regra(String nome) {
        Regra regra = regras.get(nome);
        if (regra == null) {
            throw new IllegalArgumentException("Regra de limite sem configuração: coe.limites.regras." + nome);
        }
        return regra;
    }

    /**
     * Uma regra: {@code capacidade} pedidos por {@code janela}, repostos de uma vez no fim da janela.
     * {@code fatias} > 1 só nos globais: a chave é dividida em N linhas sorteadas, cada uma com
     * capacidade/N, para a trava não serializar todo mundo numa linha só (H1).
     */
    public record Regra(
            long capacidade,
            Duration janela,
            @DefaultValue("1") int fatias) {

        public Regra {
            if (capacidade < 1 || janela == null || janela.isNegative() || janela.isZero() || fatias < 1) {
                throw new IllegalStateException("Regra de limite inválida: capacidade >= 1, janela positiva e"
                        + " fatias >= 1 (veio " + capacidade + ", " + janela + ", " + fatias + ")");
            }
            if (fatias > capacidade) {
                throw new IllegalStateException(
                        "Regra de limite inválida: mais fatias (" + fatias + ") que capacidade (" + capacidade + ")");
            }
        }

        long capacidadePorFatia() {
            return capacidade / fatias;
        }
    }
}
