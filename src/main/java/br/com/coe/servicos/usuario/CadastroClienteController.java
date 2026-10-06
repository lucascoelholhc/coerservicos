package br.com.coe.servicos.usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/** Cadastro público de cliente (RF03). O login é outro endpoint (CORE-03). */
@RestController
@RequestMapping("/api/contas")
class CadastroClienteController {

    private final CadastroClienteService cadastro;

    CadastroClienteController(CadastroClienteService cadastro) {
        this.cadastro = cadastro;
    }

    @PostMapping("/cliente")
    @Publico
    @ResponseStatus(HttpStatus.CREATED)
    ClienteCriadoResponse cadastrar(@Valid @RequestBody NovoClienteRequest pedido, HttpServletRequest requisicao) {
        return cadastro.cadastrar(pedido, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
    }
}
