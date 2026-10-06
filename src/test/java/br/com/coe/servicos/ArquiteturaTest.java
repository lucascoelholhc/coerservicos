package br.com.coe.servicos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Regras de arquitetura do código de produção. Hoje: toda data e hora vem do {@link Clock}
 * injetado (CORE-13), para que prazos (12 h, 48 h, janela de 7 dias, 18 anos) sejam testáveis.
 */
class ArquiteturaTest {

    @Test
    @DisplayName("o código de produção não lê o relógio do sistema sem o Clock injetado")
    void producaoUsaOClockInjetado() {
        JavaClasses producao = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.coe.servicos");

        RegrasDeArquitetura.SEM_RELOGIO_DO_SISTEMA.check(producao);
    }

    @Test
    @DisplayName("todo endpoint de produção tem @PreAuthorize ou @Publico (negar por padrão)")
    void endpointsDeclaramAutorizacao() {
        JavaClasses producao = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.coe.servicos");

        RegrasDeArquitetura.ENDPOINT_COM_AUTORIZACAO.check(producao);
    }

    @Test
    @DisplayName("a regra pega endpoint sem @PreAuthorize nem @Publico")
    void regraPegaEndpointSemAutorizacao() {
        JavaClasses classes = new ClassFileImporter().importClasses(ControllerSemAutorizacao.class);

        assertThatThrownBy(() -> RegrasDeArquitetura.ENDPOINT_COM_AUTORIZACAO.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("semNada");
    }

    @Test
    @DisplayName("endpoint com @PreAuthorize ou @Publico passa na regra")
    void regraAceitaEndpointDeclarado() {
        JavaClasses classes = new ClassFileImporter().importClasses(ControllerDeclarado.class);

        assertThatCode(() -> RegrasDeArquitetura.ENDPOINT_COM_AUTORIZACAO.check(classes))
                .doesNotThrowAnyException();
    }

    @org.springframework.web.bind.annotation.RestController
    static class ControllerSemAutorizacao {
        @org.springframework.web.bind.annotation.GetMapping("/exemplo/sem-nada")
        String semNada() {
            return "";
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class ControllerDeclarado {
        @org.springframework.web.bind.annotation.GetMapping("/exemplo/publico")
        @br.com.coe.servicos.compartilhado.seguranca.Publico
        String publico() {
            return "";
        }

        @org.springframework.web.bind.annotation.PostMapping("/exemplo/cliente")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('CLIENTE')")
        String cliente() {
            return "";
        }

        String auxiliarSemMapeamento() {
            return "";
        }
    }

    @ParameterizedTest(name = "pega {0}")
    @ValueSource(
            classes = {
                UsaLocalDateNow.class,
                UsaNowComZona.class,
                UsaReferenciaAInstantNow.class,
                UsaClockSystem.class,
                UsaCurrentTimeMillis.class,
                UsaCalendar.class,
                UsaNewDate.class,
                UsaNewGregorianCalendar.class
            })
    void regraPegaCadaViolacao(Class<?> violadora) {
        JavaClasses classes = new ClassFileImporter().importClasses(violadora);

        assertThatThrownBy(() -> RegrasDeArquitetura.SEM_RELOGIO_DO_SISTEMA.check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("quem usa o Clock injetado não viola a regra")
    void clockInjetadoPassa() {
        JavaClasses classes = new ClassFileImporter().importClasses(UsaClockInjetado.class);

        assertThatCode(() -> RegrasDeArquitetura.SEM_RELOGIO_DO_SISTEMA.check(classes))
                .doesNotThrowAnyException();
    }

    // Exemplos do que não pode (e do que pode) existir no código de produção.

    static class UsaLocalDateNow {
        LocalDate hoje() {
            return LocalDate.now();
        }
    }

    static class UsaNowComZona {
        LocalDate hoje() {
            return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        }
    }

    static class UsaReferenciaAInstantNow {
        Supplier<Instant> agora() {
            return Instant::now;
        }
    }

    static class UsaClockSystem {
        Clock relogio() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }
    }

    static class UsaCurrentTimeMillis {
        long agora() {
            return System.currentTimeMillis();
        }
    }

    static class UsaCalendar {
        Calendar agora() {
            return Calendar.getInstance();
        }
    }

    static class UsaNewDate {
        Date agora() {
            return new Date();
        }
    }

    static class UsaNewGregorianCalendar {
        Calendar agora() {
            return new GregorianCalendar();
        }
    }

    static class UsaClockInjetado {
        LocalDate hoje(Clock clock) {
            return LocalDate.now(clock);
        }
    }
}
