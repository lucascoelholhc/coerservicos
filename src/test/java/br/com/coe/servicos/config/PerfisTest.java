package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Os perfis carregam só o que devem. Sobe um contexto Spring vazio (sem banco nem web) com o perfil
 * pedido: o Environment resultante é o mesmo que a aplicação real veria (application.yml + perfil).
 */
class PerfisTest {

    /** Configuração vazia: só para o Spring montar o Environment a partir dos arquivos de configuração. */
    @Configuration(proxyBeanMethods = false)
    static class SemBeans {}

    @Test
    @DisplayName("prod aplica só as migrações versionadas, sem o seed local, sem clean e sem ler .env")
    void prodSemSeedLocal() {
        Environment prod = ambiente("prod");

        assertThat(prod.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(prod.getProperty("spring.flyway.clean-disabled", Boolean.class))
                .isTrue();
        assertThat(prod.getProperty("spring.flyway.placeholders.ambiente")).isNull();
        assertThat(prod.getProperty("spring.config.import")).isNull();
        assertThat(prod.getActiveProfiles()).containsExactly("prod");
    }

    @Test
    @DisplayName("prod expõe só o health do Actuator, sem detalhes")
    void prodActuatorRestrito() {
        Environment prod = ambiente("prod");

        assertThat(prod.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("local aplica o seed (db/local) com a trava do placeholder e lê o .env")
    void localComSeed() {
        Environment local = ambiente("local");

        assertThat(local.getProperty("spring.flyway.locations")).contains("classpath:db/local");
        assertThat(local.getProperty("spring.flyway.placeholders.ambiente")).isEqualTo("local");
    }

    @Test
    @DisplayName("origens e HSTS por perfil: local e test com o front local, prod só https e com HSTS")
    void origensEHstsPorPerfil() {
        Environment local = ambiente("local");
        Environment prod = ambiente("prod");

        assertThat(local.getProperty("coe.seguranca.origens-permitidas")).isEqualTo("http://localhost:5173");
        assertThat(local.getProperty("coe.seguranca.hsts", Boolean.class)).isFalse();
        assertThat(ambiente("test").getProperty("coe.seguranca.hsts", Boolean.class))
                .isFalse();
        assertThat(prod.getProperty("coe.seguranca.hsts", Boolean.class)).isTrue();
        assertThat(prod.getProperty("coe.seguranca.origens-somente-https", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("local sobe na 8081 (a 8080 da máquina de desenvolvimento é do Apache); prod fica no padrão")
    void portaDoLocal() {
        assertThat(ambiente("local").getProperty("server.port")).isEqualTo("8081");
        assertThat(ambiente("prod").getProperty("server.port")).isNull();
    }

    @Test
    @DisplayName("nenhum perfil devolve stack trace ou mensagem interna nos erros")
    void semDetalheInternoNosErros() {
        Environment prod = ambiente("prod");

        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(prod.getProperty("spring.mvc.problemdetails.enabled", Boolean.class))
                .isTrue();
    }

    private static Environment ambiente(String perfil) {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(SemBeans.class)
                .profiles(perfil)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run()) {
            return contexto.getEnvironment();
        }
    }
}
