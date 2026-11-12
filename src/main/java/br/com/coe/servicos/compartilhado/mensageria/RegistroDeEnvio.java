package br.com.coe.servicos.compartilhado.mensageria;

import java.util.UUID;

/** Onde o despacho grava se um aviso sem segredo (linha de {@code notificacao}, DOM-11) saiu ou falhou. */
public interface RegistroDeEnvio {

    /** Sem registro (despacho montado à mão nos testes da mensageria). */
    RegistroDeEnvio NENHUM = (notificacao, enviada) -> {};

    void registrar(UUID notificacao, boolean enviada);
}
