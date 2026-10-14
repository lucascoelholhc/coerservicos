package br.com.coe.servicos.usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Quanto falta para um envio por destino voltar a valer (Retry-After, CORE-07): a espera de 60 s da
 * finalidade e o teto por hora somando as finalidades. Sem nada a esperar, 1 s (o 429 já aconteceu).
 */
final class EsperaDeEnvio {

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final Duration MINIMO = Duration.ofSeconds(1);

    private EsperaDeEnvio() {}

    /**
     * @param ultimoDaFinalidade o envio mais novo da finalidade dentro do intervalo, se houver
     * @param enviosDaHora os envios da última hora (todas as finalidades), do mais novo para o mais velho
     */
    static Duration calcular(
            Instant agora,
            Optional<Instant> ultimoDaFinalidade,
            List<Instant> enviosDaHora,
            Duration intervalo,
            int maximoPorHora) {
        Duration espera = ultimoDaFinalidade
                .map(ultimo -> Duration.between(agora, ultimo.plus(intervalo)))
                .orElse(Duration.ZERO);
        if (enviosDaHora.size() >= maximoPorHora) {
            // a contagem é "criado_em > agora - 1 h": o envio que libera a vaga sai da janela 1 h depois de feito
            Instant liberaAVaga = enviosDaHora.get(maximoPorHora - 1).plus(UMA_HORA);
            Duration ateAVaga = Duration.between(agora, liberaAVaga);
            espera = ateAVaga.compareTo(espera) > 0 ? ateAVaga : espera;
        }
        return espera.compareTo(MINIMO) < 0 ? MINIMO : espera;
    }
}
