package br.com.coe.servicos.usuario;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A conta de quem está logado (CORE-06). Usado pelo front depois do login (dia 8). */
@RestController
@RequestMapping("/api/contas")
class MinhaContaController {

    private final ConsultaDeConta consulta;

    MinhaContaController(ConsultaDeConta consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/eu")
    @PreAuthorize("isAuthenticated()")
    MinhaContaResponse eu() {
        return consulta.minhaConta();
    }
}
