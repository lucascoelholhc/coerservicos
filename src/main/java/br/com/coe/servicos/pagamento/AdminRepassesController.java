package br.com.coe.servicos.pagamento;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Repasses e alertas do financeiro no painel do admin (PA02). */
@RestController
@RequestMapping("/api/admin/financeiro")
class AdminRepassesController {

    private final AdminRepassesService servico;

    AdminRepassesController(AdminRepassesService servico) {
        this.servico = servico;
    }

    @GetMapping("/repasses")
    @PreAuthorize("hasRole('ADMIN')")
    AdminRepassesService.PaginaDeRepasses repasses(
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int pagina) {
        return servico.repasses(status, pagina);
    }

    @PostMapping("/repasses/{id}/tentar-de-novo")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void tentarDeNovo(@PathVariable UUID id) {
        servico.tentarDeNovo(id);
    }

    @GetMapping("/alertas")
    @PreAuthorize("hasRole('ADMIN')")
    AdminRepassesService.PaginaDeAlertas alertas(
            @RequestParam(defaultValue = "true") boolean abertos, @RequestParam(defaultValue = "0") int pagina) {
        return servico.alertas(abertos, pagina);
    }

    @PostMapping("/alertas/{id}/resolver")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolverAlerta(@PathVariable UUID id) {
        servico.resolverAlerta(id);
    }
}
