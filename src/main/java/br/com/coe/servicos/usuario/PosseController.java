package br.com.coe.servicos.usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Prova de posse para o cadastro (RN61). Públicos e sem Origin: não usam cookie nem credencial do
 * ambiente; o segredo é o código ou o token no corpo (mesma justificativa do confirmar e-mail).
 */
@RestController
@RequestMapping("/api/contas/posse")
class PosseController {

    record CelularRequest(@NotBlank @Size(max = 25) String celular) {
        @Override
        public String toString() {
            return "CelularRequest[celular=***]";
        }
    }

    record CodigoDoCelularRequest(
            @NotBlank @Size(max = 25) String celular,

            @NotBlank @Pattern(regexp = "\\d{6}", message = "o código tem 6 números")
            String codigo) {
        @Override
        public String toString() {
            return "CodigoDoCelularRequest[celular=***, codigo=***]";
        }
    }

    record EmailRequest(@NotBlank @Size(max = 254) String email) {
        @Override
        public String toString() {
            return "EmailRequest[email=***]";
        }
    }

    record ComprovanteResponse(String comprovante) {
        @Override
        public String toString() {
            return "ComprovanteResponse[comprovante=***]";
        }
    }

    private final ServicoDePosse posse;

    PosseController(ServicoDePosse posse) {
        this.posse = posse;
    }

    @PostMapping("/celular")
    @Publico
    ResponseEntity<MensagemResponse> pedirPeloCelular(
            @Valid @RequestBody CelularRequest pedido, HttpServletRequest requisicao) {
        posse.pedirPeloCelular(pedido.celular(), requisicao.getRemoteAddr());
        return ResponseEntity.accepted().body(new MensagemResponse(ServicoDePosse.MENSAGEM));
    }

    @PostMapping("/celular/confirmar")
    @Publico
    ComprovanteResponse confirmarCelular(@Valid @RequestBody CodigoDoCelularRequest pedido) {
        return new ComprovanteResponse(posse.confirmarCelular(pedido.celular(), pedido.codigo()));
    }

    @PostMapping("/email")
    @Publico
    ResponseEntity<MensagemResponse> pedirPeloEmail(@Valid @RequestBody EmailRequest pedido) {
        posse.pedirPeloEmail(pedido.email());
        return ResponseEntity.accepted().body(new MensagemResponse(ServicoDePosse.MENSAGEM));
    }

    @PostMapping("/email/confirmar")
    @Publico
    ComprovanteResponse confirmarEmail(@Valid @RequestBody TokenRequest pedido) {
        return new ComprovanteResponse(posse.confirmarEmail(pedido.token()));
    }
}
