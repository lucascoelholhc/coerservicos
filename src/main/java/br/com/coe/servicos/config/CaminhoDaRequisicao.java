package br.com.coe.servicos.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.util.UrlPathHelper;

/**
 * Caminho da requisição como o Spring MVC e o Security o enxergam: decodificado ({@code %63odigo} vira
 * {@code codigo}) e sem o conteúdo depois de {@code ;}. Os filtros de entrada comparam rotas com ele,
 * nunca com o {@code getRequestURI()} cru, senão um caminho codificado chega ao controller sem passar
 * pelo limite (revisão de segurança da CORE-07, H1).
 */
final class CaminhoDaRequisicao {

    private CaminhoDaRequisicao() {}

    static String de(HttpServletRequest requisicao) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(requisicao);
    }
}
