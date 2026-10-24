package br.com.coe.servicos.profissional;

import java.io.IOException;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/**
 * Cadastro do profissional (DOM-02): qualquer conta logada mexe só no próprio rascunho. Uploads por multipart
 * nas {@code RotasDeUpload}; cada rota que muda algo tem limite por usuário (e os uploads, também por IP).
 */
@RestController
@RequestMapping("/api/profissional/cadastro")
class CadastroProfissionalController {

    private final CadastroProfissionalService cadastro;
    private final ArquivosDoCadastroService arquivos;

    CadastroProfissionalController(CadastroProfissionalService cadastro, ArquivosDoCadastroService arquivos) {
        this.cadastro = cadastro;
        this.arquivos = arquivos;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    CadastroResposta buscar() {
        return cadastro.buscar();
    }

    @PutMapping("/trabalho")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta trabalho(@Valid @RequestBody PedidosDoCadastro.Trabalho pedido) {
        return cadastro.trabalho(pedido);
    }

    @PutMapping("/dados")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("dados-usuario")
    CadastroResposta dados(@Valid @RequestBody PedidosDoCadastro.Dados pedido) {
        return cadastro.dados(pedido);
    }

    @PutMapping("/servicos")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta servicos(@Valid @RequestBody PedidosDoCadastro.Servicos pedido) {
        return cadastro.servicos(pedido);
    }

    @PutMapping("/experiencia")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta experiencia(@Valid @RequestBody PedidosDoCadastro.Experiencia pedido) {
        return cadastro.experiencia(pedido);
    }

    @PutMapping("/valor")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta valor(@Valid @RequestBody PedidosDoCadastro.Valor pedido) {
        return cadastro.valor(pedido);
    }

    @PutMapping("/onde")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta onde(@Valid @RequestBody PedidosDoCadastro.Onde pedido) {
        return cadastro.onde(pedido);
    }

    @PutMapping("/dias")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta dias(@Valid @RequestBody PedidosDoCadastro.Dias pedido) {
        return cadastro.dias(pedido);
    }

    @PutMapping("/sobre")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta sobre(@Valid @RequestBody PedidosDoCadastro.Sobre pedido) {
        return cadastro.sobre(pedido);
    }

    @PutMapping("/pix")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta pix(@Valid @RequestBody PedidosDoCadastro.Pix pedido) {
        return cadastro.pix(pedido);
    }

    @PostMapping("/envio")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta enviar(@Valid @RequestBody PedidosDoCadastro.Envio pedido) {
        return cadastro.enviar(pedido);
    }

    @PostMapping(path = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("upload-usuario")
    ResponseEntity<CadastroResposta.DocumentoResposta> enviarDocumento(
            @RequestParam("tipo") @Pattern(regexp = TipoDeDocumento.CODIGOS) String tipo,
            @RequestPart("arquivo") MultipartFile arquivo)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(arquivos.enviarDocumento(tipo, arquivo.getBytes()));
    }

    @GetMapping("/documentos/{id}/url")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario") // grava "documento visto" na auditoria, que é só de inserção
    CadastroResposta.UrlResposta urlDoDocumento(@PathVariable UUID id) {
        return arquivos.urlDoDocumento(id);
    }

    @DeleteMapping("/documentos/{id}")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    ResponseEntity<Void> apagarDocumento(@PathVariable UUID id) {
        arquivos.apagarDocumento(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("upload-usuario")
    ResponseEntity<CadastroResposta.FotoResposta> enviarFoto(
            @RequestParam(name = "servico", required = false) @Size(max = 80) String servico,
            @RequestParam(name = "legenda", required = false) @Size(max = 140) String legenda,
            @RequestPart("arquivo") MultipartFile arquivo)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(arquivos.enviarFoto(servico, legenda, arquivo.getBytes()));
    }

    @PutMapping("/fotos/{id}")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta.FotoResposta descreverFoto(
            @PathVariable UUID id, @Valid @RequestBody PedidosDoCadastro.Foto pedido) {
        return arquivos.descreverFoto(id, pedido);
    }

    @DeleteMapping("/fotos/{id}")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    ResponseEntity<Void> apagarFoto(@PathVariable UUID id) {
        arquivos.apagarFoto(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fotos/{id}/url")
    @PreAuthorize("isAuthenticated()")
    @LimitadoPorUsuario("cadastro-usuario")
    CadastroResposta.UrlResposta urlDaFoto(@PathVariable UUID id) {
        return arquivos.urlDaFoto(id);
    }
}
