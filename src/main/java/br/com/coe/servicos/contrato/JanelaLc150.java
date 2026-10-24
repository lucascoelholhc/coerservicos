package br.com.coe.servicos.contrato;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Limite da LC 150/2015 (RN51): a mesma diarista com o mesmo cliente tem no máximo {@code limite} diárias em
 * <b>qualquer</b> janela de 7 dias seguidos (janela móvel, não semana fixa; confirmada com o advogado). Meia diária
 * conta como um dia. Conta as diárias que já existem (não canceladas nem reembolsadas) e as do pedido.
 */
final class JanelaLc150 {

    private static final int DIAS_DA_JANELA = 7;

    private JanelaLc150() {}

    /**
     * O primeiro dia do pedido (em ordem) que faria uma janela de 7 dias passar do limite; vazio se o pedido cabe.
     * Cada dia novo é conferido em todas as janelas que o contêm, junto com os já aceitos.
     */
    static Optional<LocalDate> primeiroExcesso(List<LocalDate> existentes, List<LocalDate> novas, int limite) {
        List<LocalDate> aceitos = new ArrayList<>(existentes);
        for (LocalDate dia : novas.stream().sorted().toList()) {
            aceitos.add(dia);
            for (LocalDate inicio = dia.minusDays(DIAS_DA_JANELA - 1L);
                    !inicio.isAfter(dia);
                    inicio = inicio.plusDays(1)) {
                if (naJanela(aceitos, inicio) > limite) {
                    return Optional.of(dia);
                }
            }
        }
        return Optional.empty();
    }

    private static long naJanela(List<LocalDate> dias, LocalDate inicio) {
        LocalDate fim = inicio.plusDays(DIAS_DA_JANELA - 1L);
        return dias.stream()
                .filter(dia -> !dia.isBefore(inicio) && !dia.isAfter(fim))
                .count();
    }
}
