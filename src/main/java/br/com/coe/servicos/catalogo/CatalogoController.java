package br.com.coe.servicos.catalogo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/** Catálogo público (DOM-01), sem login: o front monta categorias e filtros só com estes dados. */
@RestController
@RequestMapping("/api/publico")
class CatalogoController {

    private final CatalogoPublico catalogo;

    CatalogoController(CatalogoPublico catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/catalogo")
    @Publico
    CatalogoResposta catalogo() {
        return catalogo.atual();
    }
}
