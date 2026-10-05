package br.com.coe.servicos.usuario;

import java.util.List;
import java.util.UUID;

/** O que o front precisa saber de quem entrou: nada de celular, e-mail ou CPF. */
public record UsuarioResumo(UUID id, String nome, List<Papel> papeis) {

    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(
                usuario.getId(),
                usuario.getNome(),
                usuario.getPapeis().stream().sorted().toList());
    }
}
