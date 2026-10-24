/**
 * Criptografia de campo (CORE-10, RNF13): AES-256-GCM com formato versionado e kid para rotação, AAD por
 * campo, HMAC do CPF com chave própria. Dado pessoal decifrado nunca vai para log nem para toString.
 */
package br.com.coe.servicos.compartilhado.cripto;
