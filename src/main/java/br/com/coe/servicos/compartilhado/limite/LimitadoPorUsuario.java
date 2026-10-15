package br.com.coe.servicos.compartilhado.limite;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limite por usuário logado (CORE-07): o endpoint cobra um pedido do balde da regra
 * ({@code coe.limites.regras.<valor>}) com a chave = id de quem chama, antes de rodar. Sem saldo, 429
 * com Retry-After. Toda rota que muda algo precisa de um limite (CoberturaDosLimitesTest).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LimitadoPorUsuario {

    /** Nome da regra em {@code coe.limites.regras}. */
    String value();

    /**
     * Regra global cobrada só DEPOIS do limite do usuário passar (vazio = nenhuma): quem já passou do
     * próprio limite não gasta o global de todo mundo.
     */
    String global() default "";
}
