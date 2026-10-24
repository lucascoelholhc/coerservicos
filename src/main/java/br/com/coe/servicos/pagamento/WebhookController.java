package br.com.coe.servicos.pagamento;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.erro.NaoAutenticadoException;
import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Webhook do gateway (DOM-07): público (o gateway não tem login), autenticado pelo token do header. O corpo chega
 * bruto ({@code byte[]}), sem DTO rígido: é guardado como veio e lido de forma tolerante.
 */
@RestController
class WebhookController {

    private final WebhookService webhooks;

    WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/api/webhooks/pagamento")
    @Publico
    ResponseEntity<Void> receber(
            @RequestHeader(name = "asaas-access-token", required = false) String token, @RequestBody byte[] corpo) {
        // o evento com token inválido já foi guardado (e commitado) pelo serviço; só então a resposta vira 401
        if (webhooks.receber(token, corpo) == WebhookService.Resultado.TOKEN_INVALIDO) {
            throw new NaoAutenticadoException("webhook-nao-autenticado", "Token do webhook inválido.");
        }
        return ResponseEntity.ok().build();
    }
}
