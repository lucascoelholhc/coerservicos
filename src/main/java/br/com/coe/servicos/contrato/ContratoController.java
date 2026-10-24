package br.com.coe.servicos.contrato;

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

/** Contratação (DOM-06): o cliente contrata; cliente e profissional do contrato o veem (anti-IDOR no serviço). */
@RestController
@RequestMapping("/api/contratos")
class ContratoController {

    private final ContratoService contratos;

    ContratoController(ContratoService contratos) {
        this.contratos = contratos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    @LimitadoPorUsuario("contrato-usuario")
    ContratoDtos.Contrato contratar(@Valid @RequestBody ContratoDtos.NovoContrato pedido) {
        return contratos.criar(pedido);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ContratoDtos.Contrato ver(@PathVariable UUID id) {
        return contratos.ver(id);
    }
}
