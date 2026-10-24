package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DOM-08 (dia 32): sem resposta do cliente em 12 h (configurável) desde o "Terminei o dia", o job libera a diária
 * (RN38, PA08). Uma liberação só, mesmo com o job em duas instâncias (ShedLock) ou concorrendo com a aprovação do
 * cliente (RN39).
 */
class LiberacaoAutomaticaTest extends ExecucaoDeDiariaTeste {

    @Autowired
    private LiberacaoAutomatica liberacaoAutomatica;

    private UUID diaria;

    private int liberacoes() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM transacao_financeira WHERE diaria_id = ? AND tipo = 'liberacao'",
                Integer.class,
                diaria);
    }

    private void diaTerminado() throws Exception {
        pagar();
        diaria = diarias.get(0);
        trabalharODia(diaria, dia);
    }

    @Test
    @DisplayName("antes das 12 h nada muda; depois, liberada pelo sistema, com histórico e livro-caixa")
    void liberaDepoisDoPrazo() throws Exception {
        diaTerminado();
        relogio.avancar(configuracao.autoLiberacao().minusMinutes(1));
        liberacaoAutomatica.executar();
        assertThat(statusDa(diaria)).isEqualTo("aguardando");

        relogio.avancar(Duration.ofMinutes(1));
        liberacaoAutomatica.executar();
        liberacaoAutomatica.executar();

        assertThat(statusDa(diaria)).isEqualTo("liberada");
        assertThat(jdbc.queryForObject("SELECT liberada_por FROM diaria WHERE id = ?", String.class, diaria))
                .isEqualTo("automatica");
        assertThat(jdbc.queryForList(
                        "SELECT de_status || '>' || para_status || '/' || origem FROM historico_diaria"
                                + " WHERE diaria_id = ? ORDER BY id",
                        String.class,
                        diaria))
                .endsWith("aguardando>liberada/sistema");
        assertThat(liberacoes()).isEqualTo(1);
        assertThat(saldoNoContrato("custodia")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("aprovação do cliente e job ao mesmo tempo: uma liberação só")
    void concorreComAprovacao() throws Exception {
        diaTerminado();
        relogio.avancar(configuracao.autoLiberacao());

        List<Integer> resultados = aoMesmoTempo(
                () -> aprovar(doCliente(), diaria).andReturn().getResponse().getStatus(), () -> {
                    liberacaoAutomatica.executar();
                    return 0;
                });

        assertThat(statusDa(diaria)).isEqualTo("liberada");
        assertThat(liberacoes()).isEqualTo(1);
        // o cliente aprovou (200) ou chegou depois do job (422 transicao-invalida)
        assertThat(resultados.get(0)).isIn(200, 422);
    }

    @Test
    @DisplayName("o job em duas instâncias ao mesmo tempo: uma liberação só")
    void duasInstancias() throws Exception {
        diaTerminado();
        relogio.avancar(configuracao.autoLiberacao());

        aoMesmoTempo(
                () -> {
                    liberacaoAutomatica.executar();
                    return 0;
                },
                () -> {
                    liberacaoAutomatica.executar();
                    return 0;
                });

        assertThat(statusDa(diaria)).isEqualTo("liberada");
        assertThat(liberacoes()).isEqualTo(1);
    }

    @SafeVarargs
    private static List<Integer> aoMesmoTempo(Callable<Integer>... tarefas) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(tarefas.length);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> tarefa : tarefas) {
                futuros.add(threads.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            threads.shutdownNow();
        }
    }
}
