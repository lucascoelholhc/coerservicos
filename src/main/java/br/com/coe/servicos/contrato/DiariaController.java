package br.com.coe.servicos.contrato;

import java.io.IOException;
import java.util.UUID;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Execução da diária (DOM-08): o profissional da diária marca Cheguei, manda fotos e termina o dia. */
@RestController
@RequestMapping("/api/diarias")
class DiariaController {

    private final DiariaService diarias;

    DiariaController(DiariaService diarias) {
        this.diarias = diarias;
    }

    @PostMapping("/{id}/cheguei")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("diaria-usuario")
    DiariaDtos.Diaria cheguei(@PathVariable UUID id) {
        return diarias.cheguei(id);
    }

    @PostMapping(path = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("upload-usuario")
    ResponseEntity<DiariaDtos.FotoEnviada> foto(
            @RequestParam("diaria") UUID diaria, @RequestPart("arquivo") MultipartFile arquivo) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(diarias.foto(diaria, arquivo.getBytes()));
    }

    @GetMapping("/{id}/fotos")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("foto-url-usuario") // assina URLs do bucket (limite próprio: ver fotos não gasta o das ações)
    DiariaDtos.FotosDoDia fotos(@PathVariable UUID id) {
        return diarias.fotos(id);
    }

    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasRole('CLIENTE')")
    @LimitadoPorUsuario("diaria-usuario")
    DiariaDtos.Diaria aprovar(@PathVariable UUID id) {
        return diarias.aprovar(id);
    }

    @PostMapping("/{id}/terminei")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("diaria-usuario")
    DiariaDtos.Diaria terminei(@PathVariable UUID id, @Valid @RequestBody DiariaDtos.Terminei pedido) {
        return diarias.terminei(id, pedido.recado());
    }
}
