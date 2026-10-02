package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.RelogioAjustavel;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;

/** Comportamento do cache da configuração depois da subida, com fonte e relógio controlados. */
class ConfiguracaoNegocioCacheTest {

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final Map<String, String> banco = completa();
    private final AtomicInteger leituras = new AtomicInteger();
    private boolean bancoFora;

    private final FonteConfiguracao fonte = () -> {
        leituras.incrementAndGet();
        if (bancoFora) {
            throw new IllegalStateException("banco indisponível");
        }
        return Map.copyOf(banco);
    };

    private static Map<String, String> completa() {
        Map<String, String> valores = new HashMap<>();
        valores.put("COMISSAO", "0.10");
        valores.put("TAXA_PAGA_POR", "cliente");
        valores.put("AUTO_LIBERA_HORAS", "12");
        valores.put("PRAZO_DISPUTA_HORAS", "48");
        valores.put("LIMITE_DOMESTICO_SEMANA", "2");
        valores.put("TENTATIVAS_ANTES_ANALISE", "3");
        valores.put("MEIA_DIARIA_HORAS", "4");
        valores.put("PAGAMENTO_EXPIRA_MINUTOS", "60");
        valores.put("VERSAO_TERMOS", "1.0");
        return valores;
    }

    @Test
    @DisplayName("parametros() entrega comissão e quem paga do mesmo snapshot")
    void snapshotCoerente() {
        ConfiguracaoNegocio configuracao = new ConfiguracaoNegocio(fonte, relogio);

        ConfiguracaoNegocio.Parametros parametros = configuracao.parametros();
        banco.put("COMISSAO", "0.20");
        banco.put("TAXA_PAGA_POR", "profissional");
        relogio.avancar(Duration.ofMinutes(5));

        assertThat(parametros.comissao()).isEqualTo(Percentual.de("0.10"));
        assertThat(parametros.taxaPagaPor().codigo()).isEqualTo("cliente");
        assertThat(configuracao.parametros().comissao()).isEqualTo(Percentual.de("0.20"));
    }

    @Test
    @DisplayName("se a releitura falhar depois da subida, mantém os últimos valores válidos")
    void mantemUltimoValidoQuandoReleituraFalha() {
        ConfiguracaoNegocio configuracao = new ConfiguracaoNegocio(fonte, relogio);
        bancoFora = true;
        relogio.avancar(Duration.ofMinutes(5));

        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));

        banco.put("AUTO_LIBERA_HORAS", "-1");
        bancoFora = false;
        relogio.avancar(Duration.ofMinutes(5));
        assertThat(configuracao.autoLiberacao()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    @DisplayName("depois de uma releitura que falhou, espera o prazo do cache antes de tentar de novo")
    void naoMartelaOBancoDepoisDeFalhar() {
        ConfiguracaoNegocio configuracao = new ConfiguracaoNegocio(fonte, relogio);
        bancoFora = true;
        relogio.avancar(Duration.ofMinutes(5));
        configuracao.comissao();
        int leiturasDepoisDaFalha = leituras.get();

        configuracao.comissao();
        configuracao.comissao();

        assertThat(leituras.get()).isEqualTo(leiturasDepoisDaFalha);
    }

    @Test
    @DisplayName("dentro dos 60 s não relê; invalidarCache() força a releitura")
    void invalidarForcaReleitura() {
        ConfiguracaoNegocio configuracao = new ConfiguracaoNegocio(fonte, relogio);
        banco.put("COMISSAO", "0.20");

        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));
        configuracao.invalidarCache();
        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.20"));
    }

    @Test
    @DisplayName("se o relógio voltar no tempo, relê em vez de confiar num cache do futuro")
    void relogioParaTras() {
        relogio.fixarEm(RelogioAjustavel.INSTANTE_PADRAO.plus(Duration.ofHours(1)));
        ConfiguracaoNegocio configuracao = new ConfiguracaoNegocio(fonte, relogio);
        banco.put("COMISSAO", "0.20");

        relogio.voltarAoPadrao();

        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.20"));
    }
}
