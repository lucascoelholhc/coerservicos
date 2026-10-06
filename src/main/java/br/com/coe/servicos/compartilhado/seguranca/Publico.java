package br.com.coe.servicos.compartilhado.seguranca;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint aberto, sem login (negar por padrão, CORE-06). Todo método de @RestController tem esta
 * anotação ou um @PreAuthorize (ArchUnit). A rota também precisa estar em
 * {@code SegurancaConfig.ROTAS_PUBLICAS}; um teste confere que as duas listas são iguais.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Publico {}
