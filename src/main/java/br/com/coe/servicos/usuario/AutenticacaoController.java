package br.com.coe.servicos.usuario;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.erro.NaoAutenticadoException;

/** Login, renovação e saída (CORE-03, PA03). O refresh só trafega no cookie HttpOnly. */
@RestController
@RequestMapping("/api/auth")
class AutenticacaoController {

    private final ServicoDeLogin login;
    private final ServicoDeSessao sessoes;
    private final EmissorDeToken emissor;
    private final CookieDeRenovacao cookie;

    AutenticacaoController(
            ServicoDeLogin login, ServicoDeSessao sessoes, EmissorDeToken emissor, CookieDeRenovacao cookie) {
        this.login = login;
        this.sessoes = sessoes;
        this.emissor = emissor;
        this.cookie = cookie;
    }

    @PostMapping("/entrar")
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest pedido, HttpServletRequest requisicao) {
        ServicoDeLogin.LoginAceito aceito =
                login.entrar(pedido, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
        return responder(aceito.usuario(), aceito.refresh());
    }

    @PostMapping("/renovar")
    ResponseEntity<SessaoResponse> renovar(
            @CookieValue(name = CookieDeRenovacao.NOME, required = false) String refresh,
            HttpServletRequest requisicao) {
        ServicoDeSessao.Renovada renovada = sessoes.renovar(
                        refresh, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT))
                .orElseThrow(() -> new NaoAutenticadoException(
                        "sessao-expirada",
                        "Sua sessão terminou. Entre de novo.",
                        Map.of(HttpHeaders.SET_COOKIE, cookie.apagar())));
        return responder(renovada.usuario(), renovada.refresh());
    }

    @PostMapping("/sair")
    ResponseEntity<Void> sair(
            @AuthenticationPrincipal Jwt token,
            @CookieValue(name = CookieDeRenovacao.NOME, required = false) String refresh) {
        sessoes.sair(UUID.fromString(token.getSubject()), refresh);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .build();
    }

    @PostMapping("/sair-de-todos")
    ResponseEntity<Void> sairDeTodos(@AuthenticationPrincipal Jwt token) {
        sessoes.sairDeTodos(UUID.fromString(token.getSubject()));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .build();
    }

    private ResponseEntity<SessaoResponse> responder(UsuarioResumo usuario, String refresh) {
        TokenDeAcesso acesso = emissor.emitir(usuario.id(), Set.copyOf(usuario.papeis()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.criar(refresh))
                .body(new SessaoResponse(acesso.valor(), acesso.expiraEm(), usuario));
    }
}
