package br.com.coe.servicos.usuario.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** E-mail válido depois de tirar espaços e passar para minúsculas (nulo passa: use @NotBlank). */
@Documented
@Constraint(validatedBy = EmailValidoValidador.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EmailValido {

    String message() default "Escreva um e-mail válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
