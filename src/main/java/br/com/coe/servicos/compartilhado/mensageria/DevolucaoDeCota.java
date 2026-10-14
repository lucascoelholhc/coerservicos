package br.com.coe.servicos.compartilhado.mensageria;

/**
 * Devolve a cota de envio de uma mensagem descartada pela fila cheia (CORE-07): a linha que a gerou
 * sai das contagens do limite por destino. Implementada no domínio dono das linhas (usuario); erro aqui
 * nunca estoura para quem publicou a mensagem.
 */
@FunctionalInterface
public interface DevolucaoDeCota {

    void devolver(MensagemPronta.Cota cota);
}
