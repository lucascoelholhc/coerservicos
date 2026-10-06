package br.com.coe.servicos.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/** Dados da conta de quem chama: o id vem só do token (anti-IDOR). */
@Service
class ConsultaDeConta {

    private final UsuarioRepository usuarios;
    private final UsuarioAutenticado usuarioAutenticado;

    ConsultaDeConta(UsuarioRepository usuarios, UsuarioAutenticado usuarioAutenticado) {
        this.usuarios = usuarios;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @Transactional(readOnly = true)
    MinhaContaResponse minhaConta() {
        return usuarios.findById(usuarioAutenticado.id())
                .map(MinhaContaResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }
}
