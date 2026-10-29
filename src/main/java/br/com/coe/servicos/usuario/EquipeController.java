package br.com.coe.servicos.usuario;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Equipe de administradores (DOM-11): listar, convidar, remover; o convidado vê e aceita o convite. */
@RestController
class EquipeController {

    private final EquipeService equipe;

    EquipeController(EquipeService equipe) {
        this.equipe = equipe;
    }

    @GetMapping("/api/admin/equipe")
    @PreAuthorize("hasRole('ADMIN')")
    EquipeService.Equipe listar() {
        return equipe.equipe();
    }

    @PostMapping("/api/admin/equipe/codigo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("envio-usuario")
    void codigoDoAdmin(HttpServletRequest requisicao) {
        equipe.pedirCodigo(requisicao.getRemoteAddr());
    }

    @PostMapping("/api/admin/equipe/convites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("equipe-usuario")
    void convidar(@Valid @RequestBody EquipeService.NovoConvite pedido) {
        equipe.convidar(pedido);
    }

    @PostMapping("/api/admin/equipe/{usuarioId}/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("equipe-usuario")
    void remover(@PathVariable UUID usuarioId, @Valid @RequestBody EquipeService.Codigo pedido) {
        equipe.remover(usuarioId, pedido);
    }

    @GetMapping("/api/contas/eu/convite-admin")
    @PreAuthorize("isAuthenticated()")
    EquipeService.MeuConvite meuConvite() {
        return equipe.meuConvite().orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
    }

    @PostMapping("/api/contas/eu/convite-admin/codigo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("envio-usuario")
    void codigoDoConvite(HttpServletRequest requisicao) {
        equipe.meuConvite().orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
        equipe.pedirCodigo(requisicao.getRemoteAddr());
    }

    @PostMapping("/api/contas/eu/convite-admin/aceitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("equipe-usuario")
    void aceitar(@Valid @RequestBody EquipeService.Codigo pedido) {
        equipe.aceitar(pedido);
    }
}
