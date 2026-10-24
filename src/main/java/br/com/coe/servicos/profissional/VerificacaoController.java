package br.com.coe.servicos.profissional;

import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/**
 * Verificação do cadastro pelo admin (DOM-03, RF49): fila, checklist, aprovar, pedir correção e recusar. Só
 * ADMIN (também pela URL {@code /api/admin/**}); cada rota que decide ou abre arquivo tem limite por usuário.
 */
@RestController
@RequestMapping("/api/admin/verificacao")
class VerificacaoController {

    private final VerificacaoService verificacao;

    VerificacaoController(VerificacaoService verificacao) {
        this.verificacao = verificacao;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    VerificacaoResposta.Fila fila(@RequestParam(defaultValue = "0") int pagina) {
        return verificacao.fila(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-detalhe-usuario") // mostra o CPF completo: limite próprio contra coleta em série
    VerificacaoResposta.Detalhe detalhe(@PathVariable UUID id) {
        return verificacao.detalhe(id);
    }

    @GetMapping("/{id}/documentos/{documento}/url")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    CadastroResposta.UrlResposta urlDoDocumento(@PathVariable UUID id, @PathVariable UUID documento) {
        return verificacao.urlDoDocumento(id, documento);
    }

    @GetMapping("/{id}/fotos/{foto}/url")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    CadastroResposta.UrlResposta urlDaFoto(@PathVariable UUID id, @PathVariable UUID foto) {
        return verificacao.urlDaFoto(id, foto);
    }

    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    VerificacaoResposta.Decisao aprovar(
            @PathVariable UUID id, @Valid @RequestBody PedidosDaVerificacao.Aprovacao pedido) {
        return verificacao.aprovar(id, pedido);
    }

    @PostMapping("/{id}/correcao")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    VerificacaoResposta.Decisao pedirCorrecao(
            @PathVariable UUID id, @Valid @RequestBody PedidosDaVerificacao.Motivo pedido) {
        return verificacao.pedirCorrecao(id, pedido);
    }

    @PostMapping("/{id}/recusar")
    @PreAuthorize("hasRole('ADMIN')")
    @LimitadoPorUsuario("admin-usuario")
    VerificacaoResposta.Decisao recusar(@PathVariable UUID id, @Valid @RequestBody PedidosDaVerificacao.Motivo pedido) {
        return verificacao.recusar(id, pedido);
    }
}
