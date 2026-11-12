package br.com.coe.servicos.profissional;

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

/** Pendências de perfis já aprovados no painel do admin (RN15a): foto e documento novos. */
@RestController
@RequestMapping("/api/admin/profissionais")
class AdminPendenciasController {

    private final AdminPendenciasService servico;

    AdminPendenciasController(AdminPendenciasService servico) {
        this.servico = servico;
    }

    @GetMapping("/pendencias")
    @PreAuthorize("hasRole('ADMIN')")
    AdminPendenciasService.Lista listar() {
        return servico.listar();
    }

    @PostMapping("/{id}/fotos/{foto}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void aprovarFoto(@PathVariable UUID id, @PathVariable UUID foto) {
        servico.aprovarFoto(id, foto);
    }

    @PostMapping("/{id}/fotos/{foto}/recusar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recusarFoto(@PathVariable UUID id, @PathVariable UUID foto) {
        servico.recusarFoto(id, foto);
    }

    @PostMapping("/{id}/documentos/{documento}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void aprovarDocumento(@PathVariable UUID id, @PathVariable UUID documento) {
        servico.aprovarDocumento(id, documento);
    }

    @PostMapping("/{id}/documentos/{documento}/recusar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recusarDocumento(
            @PathVariable UUID id,
            @PathVariable UUID documento,
            @Valid @RequestBody AdminPendenciasService.Recusa recusa) {
        servico.recusarDocumento(id, documento, recusa);
    }
}
