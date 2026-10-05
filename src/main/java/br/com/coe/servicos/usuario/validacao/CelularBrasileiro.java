package br.com.coe.servicos.usuario.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Celular brasileiro com DDD, com ou sem máscara (nulo passa: use @NotBlank para obrigar). */
@Documented
@Constraint(validatedBy = CelularBrasileiroValidador.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CelularBrasileiro {

    String message() default "Escreva seu celular com DDD";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
