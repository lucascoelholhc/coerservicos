package br.com.coe.servicos.compartilhado.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

import br.com.coe.servicos.IntegracaoTest;

/**
 * CORE-11: a configuração real do Logback (logback-spring.xml) mascara a mensagem e a mensagem da
 * exceção no texto (local e test); o JSON do prod passa pelo mesmo filtro. O SMS falso só escapa da
 * máscara no perfil local.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogsSemDadoPessoalTest extends IntegracaoTest {

    private static final Logger LOG = LoggerFactory.getLogger("br.com.coe.servicos.teste.LogsSemDadoPessoal");
    private static final String TOKEN = "Rq4ZB0OmTL20_U2x2DQMd2MTbCM3PNbT4frWi3iJf4Fg";

    @Test
    @DisplayName("mensagem com celular, e-mail e código sai mascarada no texto")
    void mensagem(CapturedOutput saida) {
        LOG.info("enviado para {} e {}: código é {}", "47900000101", "ana.silva@exemplo.com", "483920");

        assertThat(saida.getOut())
                .contains("enviado para")
                .doesNotContain("47900000101")
                .doesNotContain("ana.silva@exemplo.com")
                .doesNotContain("483920");
    }

    @Test
    @DisplayName("exceção: a mensagem (e a da causa) sai mascarada e a pilha continua legível")
    void excecao(CapturedOutput saida) {
        LOG.error(
                "falhou",
                new IllegalStateException("para ana@exemplo.com", new IllegalArgumentException("token " + TOKEN)));

        assertThat(saida.getOut())
                .doesNotContain("ana@exemplo.com")
                .doesNotContain(TOKEN)
                .contains("java.lang.IllegalStateException: para a***@exemplo.com")
                .contains("Caused by: java.lang.IllegalArgumentException: token ")
                .contains("\tat br.com.coe.servicos.compartilhado.log.LogsSemDadoPessoalTest.excecao(");
    }

    @Test
    @DisplayName("fora do perfil local, o logger do SMS falso também sai mascarado")
    void smsFalsoForaDoLocal(CapturedOutput saida) {
        LoggerFactory.getLogger("coe.sms-falso").warn("SMS FALSO: seu código é 112233");

        assertThat(saida.getOut()).doesNotContain("112233");
    }

    @Test
    @DisplayName("JSON do prod: message, error.message e as mensagens do error.stack_trace saem mascarados")
    void json() {
        JsonWriter<Object> escritor = JsonWriter.of(membros -> {
            membros.add("message", "celular 47900000101");
            membros.add("error").usingMembers(erro -> {
                erro.add("message", "para ana@exemplo.com");
                erro.add("stack_trace", "java.lang.X: token " + TOKEN + "\n\tat br.com.coe.A.b(A.java:1)\n");
            });
            new MascaraNoJsonDoLog().customize(membros);
        });

        String json = escritor.writeToString(new Object());

        assertThat(json)
                .doesNotContain("47900000101")
                .doesNotContain("ana@exemplo.com")
                .doesNotContain(TOKEN)
                .contains("at br.com.coe.A.b(A.java:1)");
    }

    @Test
    @DisplayName("JSON (ECS) só no prod, com o filtro de máscara; local e test em texto")
    void jsonSoNoProd() {
        Properties prod = yaml("application-prod.yml");
        Properties base = yaml("application.yml");

        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(prod.getProperty("logging.structured.json.customizer"))
                .isEqualTo(MascaraNoJsonDoLog.class.getName());
        assertThat(base.getProperty("logging.structured.format.console")).isNull();
    }

    private static Properties yaml(String arquivo) {
        YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
        fabrica.setResources(new ClassPathResource(arquivo));
        return fabrica.getObject();
    }
}
