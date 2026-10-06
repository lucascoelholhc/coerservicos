package br.com.coe.servicos.usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A conta de quem está logado (CORE-06), confirmação do celular e MFA (CORE-04). */
@RestController
@RequestMapping("/api/contas")
class MinhaContaController {

    private final ConsultaDeConta consulta;
    private final ServicoDeCelularEMfa celularEMfa;

    MinhaContaController(ConsultaDeConta consulta, ServicoDeCelularEMfa celularEMfa) {
        this.consulta = consulta;
        this.celularEMfa = celularEMfa;
    }

    @GetMapping("/eu")
    @PreAuthorize("isAuthenticated()")
    MinhaContaResponse eu() {
        return consulta.minhaConta();
    }

    @PostMapping("/eu/celular/codigo")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<MensagemResponse> pedirCodigoDoCelular(HttpServletRequest requisicao) {
        celularEMfa.pedirCodigoDeConfirmacao(requisicao.getRemoteAddr());
        return ResponseEntity.accepted().body(new MensagemResponse(ServicoDeCelularEMfa.MENSAGEM_CODIGO_ENVIADO));
    }

    @PostMapping("/eu/celular/confirmar")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> confirmarCelular(@Valid @RequestBody CodigoRequest pedido) {
        celularEMfa.confirmarCelular(pedido.codigo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/eu/mfa/codigo")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<MensagemResponse> pedirCodigoDoMfa(HttpServletRequest requisicao) {
        celularEMfa.pedirCodigoDeMfa(requisicao.getRemoteAddr());
        return ResponseEntity.accepted().body(new MensagemResponse(ServicoDeCelularEMfa.MENSAGEM_CODIGO_ENVIADO));
    }

    @PostMapping("/eu/mfa")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> definirMfa(@Valid @RequestBody ConfigurarMfaRequest pedido) {
        celularEMfa.definirMfa(pedido.ativo(), pedido.codigo());
        return ResponseEntity.noContent().build();
    }
}
