package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.coe.servicos.compartilhado.dinheiro.Percentual;

/** Configuração incompleta ou inválida impede a aplicação de subir, dizendo qual chave (fail fast). */
class ConfiguracaoNegocioFalhaTest {

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

    private static ApplicationContextRunner contexto(Map<String, String> valores) {
        return new ApplicationContextRunner()
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC))
                .withBean(FonteConfiguracao.class, () -> () -> Map.copyOf(valores))
                .withBean(ConfiguracaoNegocio.class);
    }

    @Test
    @DisplayName("com todas as chaves válidas, sobe e entrega os valores")
    void completaSobe() {
        contexto(completa()).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ConfiguracaoNegocio.class).comissao()).isEqualTo(Percentual.de("0.10"));
        });
    }

    @Test
    @DisplayName("sem uma chave, não sobe e a mensagem diz qual chave falta")
    void chaveAusente() {
        Map<String, String> valores = completa();
        valores.remove("LIMITE_DOMESTICO_SEMANA");

        contexto(valores).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("falta a chave LIMITE_DOMESTICO_SEMANA");
        });
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "COMISSAO, abc",
        "COMISSAO, 1.5",
        "COMISSAO, 0.31",
        "TAXA_PAGA_POR, ninguem",
        "AUTO_LIBERA_HORAS, doze",
        "PAGAMENTO_EXPIRA_MINUTOS, 1.5",
        "AUTO_LIBERA_HORAS, -1",
        "PRAZO_DISPUTA_HORAS, 0",
        "LIMITE_DOMESTICO_SEMANA, 0",
        "LIMITE_DOMESTICO_SEMANA, 3",
        "TENTATIVAS_ANTES_ANALISE, -1",
        "MEIA_DIARIA_HORAS, 0",
        "PAGAMENTO_EXPIRA_MINUTOS, 0",
        "VERSAO_TERMOS, ' '"
    })
    @DisplayName("com valor inválido, não sobe e a mensagem diz a chave e o valor")
    void valorInvalido(String chave, String valor) {
        Map<String, String> valores = completa();
        valores.put(chave, valor);

        contexto(valores).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .hasStackTraceContaining("Configuração de negócio inválida: chave " + chave + " = '" + valor + "'");
        });
    }
}
