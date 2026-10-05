package br.com.coe.servicos.usuario;

import java.util.UUID;

/** Resposta do cadastro: só o id e o nome; nada de celular, e-mail ou senha. */
public record ClienteCriadoResponse(UUID id, String nome) {}
