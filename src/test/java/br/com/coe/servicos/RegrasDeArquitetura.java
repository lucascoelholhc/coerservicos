package br.com.coe.servicos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.time.Clock;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/** Regras do ArchUnit, separadas do teste para poderem ser provadas contra exemplos. */
final class RegrasDeArquitetura {

    private static final String RELOGIO_CONFIG = "br.com.coe.servicos.config.RelogioConfig";

    /** Chamada ou referência que lê o relógio do sistema em vez do Clock injetado. */
    static final DescribedPredicate<JavaAccess<?>> LE_O_RELOGIO_DO_SISTEMA =
            DescribedPredicate.describe("lê o relógio do sistema", acesso -> leORelogioDoSistema(acesso.getTarget()));

    static final ArchRule SEM_RELOGIO_DO_SISTEMA = noClasses()
            .that()
            .resideInAPackage("br.com.coe.servicos..")
            .and()
            .doNotHaveFullyQualifiedName(RELOGIO_CONFIG)
            .should()
            .accessTargetWhere(LE_O_RELOGIO_DO_SISTEMA)
            .because("data e hora vêm sempre do Clock injetado (CORE-13)");

    /** Negar por padrão (CORE-06): todo endpoint declara quem pode chamá-lo. */
    static final ArchRule ENDPOINT_COM_AUTORIZACAO = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController.class)
            .and()
            .areMetaAnnotatedWith(RequestMapping.class)
            .should()
            .beAnnotatedWith(PreAuthorize.class)
            .orShould()
            .beAnnotatedWith(Publico.class)
            .because("negar por padrão: todo endpoint tem @PreAuthorize ou @Publico (CORE-06)");

    private RegrasDeArquitetura() {}

    private static boolean leORelogioDoSistema(AccessTarget alvo) {
        if (!(alvo instanceof CodeUnitAccessTarget unidade)) {
            return false;
        }
        JavaClass dono = unidade.getOwner();
        String nome = unidade.getName();
        List<JavaClass> parametros = unidade.getRawParameterTypes();
        boolean recebeClock = parametros.stream().anyMatch(tipo -> tipo.isEquivalentTo(Clock.class));
        boolean construtorSemArgumentos = nome.equals("<init>") && parametros.isEmpty();
        return (dono.getPackageName().equals("java.time") && nome.equals("now") && !recebeClock)
                || (dono.isEquivalentTo(Clock.class) && nome.startsWith("system"))
                || (dono.isEquivalentTo(System.class) && nome.equals("currentTimeMillis"))
                || (dono.isEquivalentTo(Calendar.class) && nome.equals("getInstance"))
                || (dono.isEquivalentTo(Date.class) && construtorSemArgumentos)
                || (dono.isEquivalentTo(GregorianCalendar.class) && construtorSemArgumentos);
    }
}
