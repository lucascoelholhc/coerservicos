package br.com.coe.servicos.profissional;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Busca e perfil público do profissional (DOM-04), sem login. Limite por IP dos GETs públicos
 * ({@code publico-ip}, FiltroDeLimites). Nenhum contato na resposta (RN22).
 */
@RestController
@RequestMapping("/api/publico/profissionais")
class BuscaPublicaController {

    private final BuscaPublicaService busca;

    BuscaPublicaController(BuscaPublicaService busca) {
        this.busca = busca;
    }

    @GetMapping
    @Publico
    BuscaResposta.Busca buscar(
            @RequestParam(required = false) String profissao,
            @RequestParam(required = false) String servico,
            @RequestParam(required = false) Integer cidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
            @RequestParam(required = false) String valorMaximo,
            @RequestParam(required = false) Integer experienciaMinima,
            @RequestParam(defaultValue = "false") boolean meiaDiaria,
            @RequestParam(required = false) String ferramentas,
            @RequestParam(required = false) String ordem,
            @RequestParam(defaultValue = "0") int pagina) {
        return busca.buscar(new BuscaPublicaService.Pedido(
                profissao,
                servico,
                cidade,
                dia,
                valorMaximo,
                experienciaMinima,
                meiaDiaria,
                ferramentas,
                ordem,
                pagina));
    }

    @GetMapping("/{id}")
    @Publico
    BuscaResposta.Perfil perfil(@PathVariable UUID id) {
        return busca.perfil(id);
    }
}
