package br.com.coe.servicos.avaliacao;

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

/** Avaliações (DOM-10): o cliente avalia o profissional e o profissional avalia o cliente, uma vez por contrato. */
@RestController
@RequestMapping("/api/avaliacoes")
class AvaliacaoController {

    private final AvaliacaoService avaliacoes;

    AvaliacaoController(AvaliacaoService avaliacoes) {
        this.avaliacoes = avaliacoes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROFISSIONAL')")
    @LimitadoPorUsuario("avaliacao-usuario")
    AvaliacaoDtos.Avaliacao avaliar(@Valid @RequestBody AvaliacaoDtos.NovaAvaliacao pedido) {
        return avaliacoes.avaliar(pedido);
    }

    @GetMapping("/contratos/{contratoId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROFISSIONAL')")
    AvaliacaoDtos.Situacao situacao(@PathVariable UUID contratoId) {
        return avaliacoes.situacao(contratoId);
    }
}
