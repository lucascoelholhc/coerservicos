package br.com.coe.servicos.config;

import org.springframework.http.HttpMethod;

/** Rota liberada sem login. A lista fica em {@link SegurancaConfig#ROTAS_PUBLICAS}. */
public record RotaPublica(HttpMethod metodo, String caminho) {}
