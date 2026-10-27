package br.com.coe.servicos.disputa;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Disputas no painel (DOM-09, RN44): fila, detalhe e decisão. Só ADMIN; toda decisão é auditada. */
@RestController
@RequestMapping("/api/admin/disputas")
class AdminDisputaController {

    private final DisputaService disputas;

    AdminDisputaController(DisputaService disputas) {
        this.disputas = disputas;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    DisputaDtos.Fila fila(@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int pagina) {
        return disputas.fila(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    DisputaDtos.Disputa ver(@PathVariable UUID id) {
        return disputas.verComoAdmin(id);
    }

    @PostMapping("/{id}/decisao")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    DisputaDtos.Disputa decidir(@PathVariable UUID id, @Valid @RequestBody DisputaDtos.Decisao pedido) {
        return disputas.decidir(id, pedido);
    }
}
