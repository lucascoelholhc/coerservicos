package br.com.coe.servicos.moderacao;

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

/** Denúncias (DOM-11): quem conversa denuncia o outro lado; a equipe decide. */
@RestController
class ModeracaoController {

    private final ModeracaoService servico;

    ModeracaoController(ModeracaoService servico) {
        this.servico = servico;
    }

    @PostMapping("/api/denuncias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROFISSIONAL')")
    @LimitadoPorUsuario("denuncia-usuario")
    ModeracaoService.Criada denunciar(@Valid @RequestBody ModeracaoService.NovaDenuncia pedido) {
        return servico.denunciar(pedido);
    }

    @GetMapping("/api/admin/denuncias")
    @PreAuthorize("hasRole('ADMIN')")
    ModeracaoService.Fila fila(@RequestParam(defaultValue = "0") int pagina) {
        return servico.fila(pagina);
    }

    @PostMapping("/api/admin/denuncias/{id}/decisao")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    ModeracaoService.Criada decidir(@PathVariable UUID id, @Valid @RequestBody ModeracaoService.Decisao pedido) {
        return servico.decidir(id, pedido);
    }
}
