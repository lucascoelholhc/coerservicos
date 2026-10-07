package br.com.coe.servicos.compartilhado.seguranca;

import java.util.UUID;

/**
 * A conta está com contato pendente (RN61), segundo o banco. O token antigo (emitido antes da
 * transferência) não tem a marca e vale até 15 min; por isso o bloqueio consulta o banco também.
 */
public interface ContatoPendente {

    boolean pendente(UUID usuarioId);
}
