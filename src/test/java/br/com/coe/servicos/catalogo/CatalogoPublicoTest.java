package br.com.coe.servicos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.RelogioAjustavel;

/** Cache de 60 s do catálogo público (DOM-01), no mesmo modelo do ConfiguracaoNegocio. */
class CatalogoPublicoTest {

    private static final CatalogoResposta PRIMEIRO =
            new CatalogoResposta(List.of(), List.of(new CatalogoResposta.Cidade(4202404, "Blumenau", "SC")));
    private static final CatalogoResposta SEGUNDO =
            new CatalogoResposta(List.of(), List.of(new CatalogoResposta.Cidade(4205902, "Gaspar", "SC")));

    private final FonteCatalogo fonte = mock(FonteCatalogo.class);
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private CatalogoPublico catalogo;

    @BeforeEach
    void preparar() {
        catalogo = new CatalogoPublico(fonte, relogio);
    }

    @Test
    @DisplayName("lê o banco uma vez e guarda por 60 s; depois relê")
    void guardaPorSessentaSegundos() {
        when(fonte.ler()).thenReturn(PRIMEIRO, SEGUNDO);

        assertThat(catalogo.atual()).isEqualTo(PRIMEIRO);
        relogio.avancar(Duration.ofSeconds(59));
        assertThat(catalogo.atual()).isEqualTo(PRIMEIRO);
        verify(fonte, times(1)).ler();

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(catalogo.atual()).isEqualTo(SEGUNDO);
        verify(fonte, times(2)).ler();
    }

    @Test
    @DisplayName("invalidarCache força a releitura (para o admin, quando mudar o catálogo)")
    void invalidarForcaReleitura() {
        when(fonte.ler()).thenReturn(PRIMEIRO, SEGUNDO);
        catalogo.atual();

        catalogo.invalidarCache();

        assertThat(catalogo.atual()).isEqualTo(SEGUNDO);
    }

    @Test
    @DisplayName("se a releitura falhar, mantém o último catálogo válido")
    void falhaNaReleituraMantemOAnterior() {
        when(fonte.ler()).thenReturn(PRIMEIRO).thenThrow(new IllegalStateException("banco fora"));
        catalogo.atual();

        relogio.avancar(Duration.ofSeconds(61));

        assertThat(catalogo.atual()).isEqualTo(PRIMEIRO);
    }

    @Test
    @DisplayName("sem nenhuma leitura válida ainda, a falha sobe (vira 500 em Problem Details)")
    void falhaNaPrimeiraLeituraSobe() {
        when(fonte.ler()).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(catalogo::atual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("primeira leitura falhou: por 5 s repete a falha sem ir ao banco; depois tenta de novo")
    void falhaRecenteNaoVaiAoBanco() {
        IllegalStateException fora = new IllegalStateException("banco fora");
        when(fonte.ler()).thenThrow(fora).thenReturn(PRIMEIRO);

        assertThatThrownBy(catalogo::atual).isSameAs(fora);
        relogio.avancar(Duration.ofSeconds(4));
        assertThatThrownBy(catalogo::atual).isSameAs(fora);
        verify(fonte, times(1)).ler();

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(catalogo.atual()).isEqualTo(PRIMEIRO);
        verify(fonte, times(2)).ler();
    }

    @Test
    @DisplayName("invalidarCache também descarta a falha guardada")
    void invalidarDescartaAFalha() {
        when(fonte.ler()).thenThrow(new IllegalStateException("banco fora")).thenReturn(PRIMEIRO);
        assertThatThrownBy(catalogo::atual).isInstanceOf(IllegalStateException.class);

        catalogo.invalidarCache();

        assertThat(catalogo.atual()).isEqualTo(PRIMEIRO);
    }

    @Test
    @DisplayName("relógio que volta no tempo também invalida")
    void relogioQueVoltaInvalida() {
        when(fonte.ler()).thenReturn(PRIMEIRO, SEGUNDO);
        catalogo.atual();

        relogio.avancar(Duration.ofSeconds(-1));

        assertThat(catalogo.atual()).isEqualTo(SEGUNDO);
    }
}
