package br.com.coe.servicos.config;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Configuração de negócio pelo admin (DOM-11): ver os valores em vigor e mudar um por vez. */
@RestController
@RequestMapping("/api/admin/configuracao")
class AdminConfiguracaoController {

    private final AdminConfiguracaoService servico;

    AdminConfiguracaoController(AdminConfiguracaoService servico) {
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    AdminConfiguracaoService.Lista listar() {
        return servico.listar();
    }

    @PutMapping("/{chave}")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    AdminConfiguracaoService.Parametro mudar(
            @PathVariable String chave, @Valid @RequestBody AdminConfiguracaoService.Mudanca pedido) {
        return servico.mudar(chave, pedido);
    }
}
