package br.com.coe.servicos.usuario.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Regras da senha do cadastro; o erro sai no campo "senha". */
@Documented
@Constraint(validatedBy = SenhaPermitidaValidador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaPermitida {

    String message() default "Escolha outra senha";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
