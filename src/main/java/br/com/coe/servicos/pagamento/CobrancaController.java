package br.com.coe.servicos.pagamento;

import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Pagamento do contrato (DOM-07): só o cliente dono pede e vê a cobrança (anti-IDOR no serviço). */
@RestController
@RequestMapping("/api/contratos/{contrato}/pagamento")
class CobrancaController {

    private final CobrancaService cobrancas;

    CobrancaController(CobrancaService cobrancas) {
        this.cobrancas = cobrancas;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @LimitadoPorUsuario("pagamento-usuario")
    ResponseEntity<CobrancaDtos.Pagamento> pagar(
            @PathVariable UUID contrato, @Valid @RequestBody CobrancaDtos.NovoPagamento pedido) {
        CobrancaDtos.Resultado resultado = cobrancas.pagar(contrato, GatewayPagamento.Metodo.doCodigo(pedido.metodo()));
        return ResponseEntity.status(resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.pagamento());
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENTE')")
    CobrancaDtos.Pagamento ver(@PathVariable UUID contrato) {
        return cobrancas.ver(contrato);
    }
}
