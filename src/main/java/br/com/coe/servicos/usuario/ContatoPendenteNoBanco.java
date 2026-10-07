package br.com.coe.servicos.usuario;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.seguranca.ContatoPendente;

/** Consulta direta (uma linha pela chave): conta não excluída sem celular ou sem e-mail. */
@Component
class ContatoPendenteNoBanco implements ContatoPendente {

    private final UsuarioRepository usuarios;

    ContatoPendenteNoBanco(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean pendente(UUID usuarioId) {
        return usuarios.contarComContatoPendente(usuarioId) > 0;
    }
}
