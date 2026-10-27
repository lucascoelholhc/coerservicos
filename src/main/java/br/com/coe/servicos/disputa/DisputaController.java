package br.com.coe.servicos.disputa;

import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Reclamação de uma diária (DOM-09): o cliente reclama e escolhe as outras diárias; o profissional responde. */
@RestController
@RequestMapping("/api/disputas")
class DisputaController {

    private final DisputaService disputas;

    DisputaController(DisputaService disputas) {
        this.disputas = disputas;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    @LimitadoPorUsuario("disputa-usuario")
    DisputaDtos.Disputa reclamar(@Valid @RequestBody DisputaDtos.NovaReclamacao pedido) {
        return disputas.reclamar(pedido);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    DisputaDtos.Disputa ver(@PathVariable UUID id) {
        return disputas.ver(id);
    }

    @PostMapping("/{id}/resposta")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("disputa-usuario")
    DisputaDtos.Disputa responder(@PathVariable UUID id, @Valid @RequestBody DisputaDtos.Resposta pedido) {
        return disputas.responder(id, pedido);
    }

    @PostMapping("/{id}/demais-diarias")
    @PreAuthorize("hasRole('CLIENTE')")
    @LimitadoPorUsuario("disputa-usuario")
    DisputaDtos.Disputa demaisDiarias(@PathVariable UUID id, @Valid @RequestBody DisputaDtos.DemaisDiarias pedido) {
        return disputas.demaisDiarias(id, pedido);
    }
}
