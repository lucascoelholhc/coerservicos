package br.com.coe.servicos.usuario;

import java.util.List;
import java.util.UUID;

/**
 * O que o front precisa saber de quem entrou: nada de celular, e-mail ou CPF. {@code contatoPendente}
 * (RN61) vem sempre do banco, no login e em toda renovação.
 */
public record UsuarioResumo(UUID id, String nome, List<Papel> papeis, boolean contatoPendente) {

    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(
                usuario.getId(),
                usuario.getNome(),
                usuario.getPapeis().stream().sorted().toList(),
                usuario.isContatoPendente());
    }
}
