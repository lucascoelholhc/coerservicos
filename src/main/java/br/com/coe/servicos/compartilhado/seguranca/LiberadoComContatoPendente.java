package br.com.coe.servicos.compartilhado.seguranca;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint (ou controller inteiro) que continua aberto para quem está com contato pendente (RN61):
 * entrar, ver a conta, cadastrar e confirmar contato e, no DOM-08, Cheguei, Terminei o dia e
 * aprovar diária (a execução do que já foi pago nunca trava). O resto nega com 403. A lista é
 * congelada por teste.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LiberadoComContatoPendente {}
