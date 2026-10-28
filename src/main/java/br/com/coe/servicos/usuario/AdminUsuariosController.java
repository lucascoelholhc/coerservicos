package br.com.coe.servicos.usuario;

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

/** Contas no painel do admin (DOM-11): busca, suspender e reativar. */
@RestController
@RequestMapping("/api/admin/usuarios")
class AdminUsuariosController {

    private final AdminUsuariosService servico;

    AdminUsuariosController(AdminUsuariosService servico) {
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-detalhe-usuario")
    AdminUsuariosService.Pagina listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String papel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int pagina) {
        return servico.listar(busca, papel, status, pagina);
    }

    @PostMapping("/{id}/suspender")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AdminUsuariosService.Conta suspender(
            @PathVariable UUID id, @Valid @RequestBody AdminUsuariosService.Suspensao pedido) {
        return servico.suspender(id, pedido);
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AdminUsuariosService.Conta reativar(@PathVariable UUID id) {
        return servico.reativar(id);
    }
}
