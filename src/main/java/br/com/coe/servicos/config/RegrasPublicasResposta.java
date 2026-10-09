package br.com.coe.servicos.config;

import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;

/**
 * Regras públicas para os textos do front: comissão (fração, ex.: "0.1000"), prazo da liberação
 * automática (ISO-8601, ex.: "PT12H"), quem paga a taxa e a versão dos termos (para o aceite no
 * cadastro). Nenhum outro parâmetro sai daqui.
 */
record RegrasPublicasResposta(
        Percentual comissao, String prazoLiberacao, TaxaPagaPor taxaPagaPor, String versaoTermos) {

    static RegrasPublicasResposta de(ConfiguracaoNegocio.Parametros parametros) {
        return new RegrasPublicasResposta(
                parametros.comissao(),
                parametros.autoLiberacao().toString(),
                parametros.taxaPagaPor(),
                parametros.versaoTermos());
    }
}
