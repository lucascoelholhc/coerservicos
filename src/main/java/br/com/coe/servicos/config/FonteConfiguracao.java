package br.com.coe.servicos.config;

import java.util.Map;

/** De onde vêm os valores em vigor da configuração de negócio: chave para valor, ainda como texto. */
@FunctionalInterface
public interface FonteConfiguracao {

    Map<String, String> lerVigentes();
}
