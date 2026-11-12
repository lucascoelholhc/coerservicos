package br.com.coe.servicos.profissional;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Fotos novas de perfis já aprovados no painel do admin (RN15a); documento novo vai para a fila de verificação. */
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
}
