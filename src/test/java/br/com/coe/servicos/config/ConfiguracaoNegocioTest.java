package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;

/** Lê os parâmetros de negócio da view configuracao_vigente (CORE-12; RN31, RN38, RN51). */
class ConfiguracaoNegocioTest extends BancoIntegracaoTest {

    private static final String NOVA_COMISSAO =
            "INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) "
                    + "VALUES ('COMISSAO', ?, 'decimal', 'teste', now() + CAST(? AS interval))";

    @Autowired
    ConfiguracaoNegocio configuracao;

    @BeforeEach
    @AfterEach
    void cacheLimpo() {
        configuracao.invalidarCache();
    }

    @Test
    @DisplayName("lê os valores do seed da V1, já tipados")
    void valoresDoSeed() {
        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));
        assertThat(configuracao.taxaPagaPor()).isEqualTo(TaxaPagaPor.CLIENTE);
        assertThat(configuracao.autoLiberacao()).isEqualTo(Duration.ofHours(12));
        assertThat(configuracao.prazoDisputa()).isEqualTo(Duration.ofHours(48));
        assertThat(configuracao.limiteDiaristaJanela7Dias()).isEqualTo(2);
        assertThat(configuracao.tentativasAntesAnalise()).isEqualTo(3);
        assertThat(configuracao.meiaDiariaMaxima()).isEqualTo(Duration.ofHours(4));
        assertThat(configuracao.expiracaoPagamento()).isEqualTo(Duration.ofMinutes(60));
        assertThat(configuracao.versaoTermos()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("valor com vigência no futuro ainda não vale")
    void vigenciaFuturaAindaNaoVale() {
        jdbc.update(NOVA_COMISSAO, "0.15", "1 day");
        configuracao.invalidarCache();

        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));
    }

    @Test
    @DisplayName("nova linha com vigência passada substitui a anterior (a tabela é só de inserção)")
    void novaVigenciaSubstitui() {
        jdbc.update(NOVA_COMISSAO, "0.12", "-1 minute");
        configuracao.invalidarCache();

        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.12"));
    }

    @Test
    @DisplayName("guarda os valores por 60 s no relógio da aplicação; depois relê o banco")
    void cacheDeSessentaSegundos() {
        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));
        jdbc.update(NOVA_COMISSAO, "0.12", "-1 minute");

        relogio.avancar(Duration.ofSeconds(59));
        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.10"));

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(configuracao.comissao()).isEqualTo(Percentual.de("0.12"));
    }
}
