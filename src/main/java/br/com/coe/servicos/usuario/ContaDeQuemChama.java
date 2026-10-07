package br.com.coe.servicos.usuario;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * A conta do token (anti-IDOR). Suspensa ou excluída, com token ainda válido (até 15 min), não
 * mexe em nada: 403 conta-suspensa / 404.
 */
@Component
class ContaDeQuemChama {

    private final UsuarioRepository usuarios;
    private final UsuarioAutenticado usuarioAutenticado;

    ContaDeQuemChama(UsuarioRepository usuarios, UsuarioAutenticado usuarioAutenticado) {
        this.usuarios = usuarios;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    Usuario carregarAtiva() {
        Usuario usuario = carregar(usuarioAutenticado.id());
        if (Usuario.EXCLUIDO.equals(usuario.getStatus())) {
            throw new RecursoNaoEncontradoException("Conta não encontrada.");
        }
        if (Usuario.SUSPENSO.equals(usuario.getStatus())) {
            throw new AcaoProibidaException("conta-suspensa", "Sua conta está suspensa. Fale com a equipe da COE.");
        }
        return usuario;
    }

    Usuario carregar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }
}
