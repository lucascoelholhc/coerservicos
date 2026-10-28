package br.com.coe.servicos.avaliacao;

import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/**
 * Avaliações (DOM-10): o cliente avalia o profissional e o profissional avalia o cliente, uma vez por contrato. A equipe
 * oculta ou mostra de novo na moderação (DOM-11).
 */
@RestController
class AvaliacaoController {

    private final AvaliacaoService avaliacoes;

    AvaliacaoController(AvaliacaoService avaliacoes) {
        this.avaliacoes = avaliacoes;
    }

    @PostMapping("/api/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROFISSIONAL')")
    @LimitadoPorUsuario("avaliacao-usuario")
    AvaliacaoDtos.Avaliacao avaliar(@Valid @RequestBody AvaliacaoDtos.NovaAvaliacao pedido) {
        return avaliacoes.avaliar(pedido);
    }

    @GetMapping("/api/avaliacoes/contratos/{contratoId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROFISSIONAL')")
    AvaliacaoDtos.Situacao situacao(@PathVariable UUID contratoId) {
        return avaliacoes.situacao(contratoId);
    }

    @GetMapping("/api/admin/avaliacoes")
    @PreAuthorize("hasRole('ADMIN')")
    AvaliacaoService.PaginaDaModeracao listarParaModeracao(
            @RequestParam(required = false) UUID profissional, @RequestParam(defaultValue = "0") int pagina) {
        return avaliacoes.listarParaModeracao(profissional, pagina);
    }

    @PostMapping("/api/admin/avaliacoes/{id}/ocultar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AvaliacaoService.NaModeracao ocultar(@PathVariable UUID id) {
        return avaliacoes.mudarVisibilidade(id, false);
    }

    @PostMapping("/api/admin/avaliacoes/{id}/mostrar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AvaliacaoService.NaModeracao mostrar(@PathVariable UUID id) {
        return avaliacoes.mudarVisibilidade(id, true);
    }
}
