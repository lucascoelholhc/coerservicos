package br.com.coe.servicos.usuario;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.erro.NaoAutenticadoException;
import br.com.coe.servicos.compartilhado.seguranca.LiberadoComContatoPendente;
import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Login (senha, segundo passo e código SMS), renovação e saída (CORE-03, CORE-04, PA03). O refresh
 * só trafega no cookie HttpOnly.
 */
@RestController
@RequestMapping("/api/auth")
@LiberadoComContatoPendente
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
    @Publico
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest pedido, HttpServletRequest requisicao) {
        ServicoDeLogin.LoginAceito aceito =
                login.entrar(pedido, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
        return responder(aceito.usuario(), aceito.refresh());
    }

    @PostMapping("/segundo-passo")
    @Publico
    ResponseEntity<SessaoResponse> segundoPasso(
            @Valid @RequestBody SegundoPassoRequest pedido, HttpServletRequest requisicao) {
        ServicoDeLogin.LoginAceito aceito =
                login.segundoPasso(pedido, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
        return responder(aceito.usuario(), aceito.refresh());
    }

    @PostMapping("/codigo")
    @Publico
    ResponseEntity<MensagemResponse> pedirCodigo(
            @Valid @RequestBody PedidoDeCodigoRequest pedido, HttpServletRequest requisicao) {
        login.pedirCodigo(pedido.celular(), requisicao.getRemoteAddr());
        return ResponseEntity.accepted().body(new MensagemResponse(ServicoDeLogin.MENSAGEM_CODIGO_ENVIADO));
    }

    @PostMapping("/entrar-com-codigo")
    @Publico
    ResponseEntity<SessaoResponse> entrarComCodigo(
            @Valid @RequestBody EntrarComCodigoRequest pedido, HttpServletRequest requisicao) {
        ServicoDeLogin.LoginAceito aceito =
                login.entrarComCodigo(pedido, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
        return responder(aceito.usuario(), aceito.refresh());
    }

    @PostMapping("/renovar")
    @Publico
    ResponseEntity<SessaoResponse> renovar(
            @CookieValue(name = CookieDeRenovacao.NOME, required = false) String refresh,
            HttpServletRequest requisicao) {
        ServicoDeSessao.Resultado resultado =
                sessoes.renovar(refresh, requisicao.getRemoteAddr(), requisicao.getHeader(HttpHeaders.USER_AGENT));
        if (resultado instanceof ServicoDeSessao.Renovada renovada) {
            return responder(renovada.usuario(), renovada.refresh());
        }
        boolean apagarCookie = ((ServicoDeSessao.Recusada) resultado).apagarCookie();
        throw new NaoAutenticadoException(
                "sessao-expirada",
                "Sua sessão terminou. Entre de novo.",
                apagarCookie ? Map.of(HttpHeaders.SET_COOKIE, cookie.apagar()) : Map.of());
    }

    @PostMapping("/sair")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> sair(
            @AuthenticationPrincipal Jwt token,
            @CookieValue(name = CookieDeRenovacao.NOME, required = false) String refresh) {
        sessoes.sair(UUID.fromString(token.getSubject()), refresh);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .build();
    }

    @PostMapping("/sair-de-todos")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> sairDeTodos(@AuthenticationPrincipal Jwt token) {
        sessoes.sairDeTodos(UUID.fromString(token.getSubject()));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .build();
    }

    private ResponseEntity<SessaoResponse> responder(UsuarioResumo usuario, RefreshEmitido refresh) {
        TokenDeAcesso acesso = emissor.emitir(usuario.id(), Set.copyOf(usuario.papeis()), usuario.contatoPendente());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.criar(refresh))
                .body(new SessaoResponse(acesso.valor(), acesso.expiraEm(), usuario));
    }
}
