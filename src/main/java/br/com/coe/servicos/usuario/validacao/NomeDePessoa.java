package br.com.coe.servicos.usuario.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Nome de 2 a 120 caracteres, sem contar os espaços das pontas (nulo passa: use @NotBlank). */
@Documented
@Constraint(validatedBy = NomeDePessoaValidador.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NomeDePessoa {

    String message() default "Escreva seu nome, de 2 a 120 letras";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
