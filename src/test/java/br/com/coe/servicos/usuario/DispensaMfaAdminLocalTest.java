package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Até o CORE-04 (SMS), o admin do seed só entra no perfil local. Trava dupla: bean
 * {@code @Profile("local")} e propriedade explícita, que só existe no application-local.yml.
 */
class DispensaMfaAdminLocalTest {

    static class SemBeans {}

    private final ApplicationContextRunner contexto =
            new ApplicationContextRunner().withUserConfiguration(DispensaMfaAdminLocal.class);

    @Test
    @DisplayName("local + propriedade ligada: a dispensa existe")
    void localLigada() {
        contexto.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("coe.seguranca.dispensar-mfa-admin=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DispensaMfaAdminLocal.class));
    }

    @Test
    @DisplayName("local sem a propriedade: não existe")
    void localSemPropriedade() {
        contexto.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DispensaMfaAdminLocal.class));
    }

    @Test
    @DisplayName("prod ou test, mesmo com a propriedade: não existe")
    void prodOuTest() {
        for (String perfil : new String[] {"prod", "test"}) {
            contexto.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(perfil))
                    .withPropertyValues("coe.seguranca.dispensar-mfa-admin=true")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(DispensaMfaAdminLocal.class));
        }
    }

    @Test
    @DisplayName("a propriedade só está ligada no application-local.yml")
    void propriedadeSoNoLocal() {
        assertThat(ambiente("local").getProperty("coe.seguranca.dispensar-mfa-admin"))
                .isEqualTo("true");
        assertThat(ambiente("prod").getProperty("coe.seguranca.dispensar-mfa-admin"))
                .isNull();
        assertThat(ambiente("test").getProperty("coe.seguranca.dispensar-mfa-admin"))
                .isNull();
    }

    private static Environment ambiente(String perfil) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SemBeans.class)
                .profiles(perfil)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run()) {
            return ctx.getEnvironment();
        }
    }
}
