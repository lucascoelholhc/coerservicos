package br.com.coe.servicos.usuario.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** CEP com 8 dígitos, com ou sem hífen (nulo passa: use @NotBlank para obrigar). */
@Documented
@Constraint(validatedBy = CepValidoValidador.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CepValido {

    String message() default "Escreva o CEP com 8 números";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
