package br.com.coe.servicos.profissional;

import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Profissionais no painel do admin (DOM-11): lista, detalhe com faltas, suspender e reativar. */
@RestController
@RequestMapping("/api/admin/profissionais")
class AdminProfissionaisController {

    private final AdminProfissionaisService servico;

    AdminProfissionaisController(AdminProfissionaisService servico) {
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    AdminProfissionaisService.Pagina listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean alerta,
            @RequestParam(defaultValue = "0") int pagina) {
        return servico.listar(busca, status, alerta, pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    AdminProfissionaisService.Detalhe detalhe(@PathVariable UUID id) {
        return servico.detalhe(id);
    }

    @PostMapping("/{id}/suspender")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AdminProfissionaisService.Detalhe suspender(
            @PathVariable UUID id, @Valid @RequestBody AdminProfissionaisService.Suspensao pedido) {
        return servico.suspender(id, pedido);
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AdminProfissionaisService.Detalhe reativar(@PathVariable UUID id) {
        return servico.reativar(id);
    }
}
