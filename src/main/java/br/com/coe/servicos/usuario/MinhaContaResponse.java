package br.com.coe.servicos.usuario;

import java.util.List;
import java.util.UUID;

/** Os dados do próprio usuário (GET /api/contas/eu): nada de hash de senha nem status interno. */
public record MinhaContaResponse(
        UUID id, String nome, List<Papel> papeis, String email, String celular, boolean celularConfirmado) {

    static MinhaContaResponse de(Usuario usuario) {
        return new MinhaContaResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getPapeis().stream().sorted().toList(),
                usuario.getEmail(),
                usuario.getCelular(),
                usuario.isCelularConfirmado());
    }
}
