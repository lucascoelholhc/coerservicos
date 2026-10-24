package br.com.coe.servicos.mensagem;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Chat (DOM-05): qualquer logado conversa; o dono da conversa vem só do token (anti-IDOR no serviço). */
@RestController
@RequestMapping("/api/conversas")
class ConversaController {

    private final ConversaService conversas;

    ConversaController(ConversaService conversas) {
        this.conversas = conversas;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("conversa-usuario")
    ResponseEntity<ChatDtos.Conversa> abrir(@Valid @RequestBody ChatDtos.AbrirConversa pedido) {
        ConversaService.Aberta aberta = conversas.abrir(pedido.profissionalId());
        return ResponseEntity.status(aberta.criada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(aberta.conversa());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    ChatDtos.Conversas listar() {
        return conversas.listar();
    }

    @GetMapping("/{id}/mensagens")
    @PreAuthorize("isAuthenticated()")
    ChatDtos.Mensagens mensagens(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant antes) {
        return conversas.mensagens(id, antes);
    }

    @PostMapping("/{id}/mensagens")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("chat-usuario")
    ResponseEntity<ChatDtos.Envio> enviar(@PathVariable UUID id, @Valid @RequestBody ChatDtos.NovaMensagem pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversas.enviar(id, pedido.texto()));
    }

    @PostMapping("/{id}/lidas")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("chat-usuario")
    ResponseEntity<Void> lidas(@PathVariable UUID id) {
        conversas.marcarLidas(id);
        return ResponseEntity.noContent().build();
    }
}
