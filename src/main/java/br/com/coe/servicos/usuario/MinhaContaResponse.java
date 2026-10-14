package br.com.coe.servicos.usuario;

import java.util.List;
import java.util.UUID;

/**
 * Os dados do próprio usuário (GET /api/contas/eu): nada de hash de senha nem status interno.
 * {@code contatoPendente} (RN61) é o mesmo cálculo do login, a partir da conta carregada agora. {@code mfaAtivo} é a
 * escolha da pessoa (código por SMS no login); para o ADMIN o código é sempre obrigatório, mesmo com
 * {@code mfaAtivo = false} (o front mostra "obrigatório" pelo papel).
 */
public record MinhaContaResponse(
        UUID id,
        String nome,
        List<Papel> papeis,
        String email,
        String celular,
        boolean celularConfirmado,
        boolean emailConfirmado,
        boolean contatoPendente,
        boolean mfaAtivo) {

    static MinhaContaResponse de(Usuario usuario) {
        return new MinhaContaResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getPapeis().stream().sorted().toList(),
                usuario.getEmail(),
                usuario.getCelular(),
                usuario.isCelularConfirmado(),
                usuario.isEmailConfirmado(),
                usuario.isContatoPendente(),
                usuario.isMfaSmsAtivo());
    }

    /** Sem dados pessoais (nome, e-mail, celular) em log. */
    @Override
    public String toString() {
        return "MinhaContaResponse[id=" + id + ", papeis=" + papeis + "]";
    }
}
