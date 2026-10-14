package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * CORE-11: varredura dos logs. Roda os fluxos de conta que mexem com dado pessoal e segredo (cadastro,
 * entrar, código por SMS, segundo passo, posse do celular e do e-mail, esqueci, redefinir, trocar senha
 * e recolocar contato) e falha se a saída tiver o celular, o e-mail, a senha, o código, o link, o
 * comprovante ou o token usados. Fluxo novo que mexe com dado pessoal entra aqui (CLAUDE.md).
 */
@ExtendWith(OutputCaptureExtension.class)
class VarreduraDeLogsTest extends IntegracaoTest {

    /** Faixa de celulares só desta varredura (o ContasDeTeste usa 4790000xxxx). */
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCIA =
            new java.util.concurrent.atomic.AtomicLong();

    private static final String S1 = "Senha-Inicial-2026";
    private static final String S2 = "Senha-Redefinida-2026";
    private static final String S3 = "Senha-Trocada-2026";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    /** Tudo o que não pode aparecer no log; os códigos de 6 dígitos ficam à parte (conferidos isolados). */
    private final List<String> segredos = new ArrayList<>();

    private final List<String> codigos = new ArrayList<>();

    private String guardar(String segredo) {
        segredos.add(segredo);
        return segredo;
    }

    private MockHttpServletResponse executar(MockHttpServletRequestBuilder pedido, int status) throws Exception {
        return mockMvc.perform(pedido)
                .andExpect(status().is(status))
                .andReturn()
                .getResponse();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder pedido, String corpo) {
        return pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder pedido, String corpo) {
        return json(pedido.header("Origin", ORIGEM_DO_FRONT), corpo);
    }

    private String codigo(String celular) {
        String codigo = sms.ultimoCodigo(celular).orElseThrow();
        codigos.add(codigo);
        return codigo;
    }

    private String token(String endereco) {
        return guardar(email.ultimoToken(endereco).orElseThrow());
    }

    private String acesso(MockHttpServletResponse resposta) throws Exception {
        String token = JsonPath.read(resposta.getContentAsString(), "$.accessToken");
        guardar(LoginTest.refreshDoCookie(resposta));
        return "Bearer " + guardar(token);
    }

    @Test
    @DisplayName("nenhum fluxo de conta deixa dado pessoal ou segredo no log")
    void fluxosDeConta(CapturedOutput saida) throws Exception {
        ContasDeTeste contas = new ContasDeTeste(jdbc, codificador);
        String celular = guardar(String.valueOf(47_900_093_000L + SEQUENCIA.incrementAndGet()));
        String endereco = guardar("varredura-" + System.nanoTime() + "@teste.coe.local");
        guardar(S1);
        guardar(S2);
        guardar(S3);
        String versao = JsonPath.read(executar(get("/api/publico/regras"), 200).getContentAsString(), "$.versaoTermos");

        // cadastro (manda o link de confirmação do e-mail)
        executar(
                json(
                        post("/api/contas/cliente"),
                        "{\"nome\":\"Varredura\",\"celular\":\"" + celular + "\",\"email\":\"" + endereco
                                + "\",\"cep\":\"89010000\",\"senha\":\"" + S1 + "\",\"versaoTermosAceita\":\""
                                + versao + "\"}"),
                201);
        token(endereco);

        // entrar com senha e confirmar o celular
        String bearer = acesso(executar(
                json(post("/api/auth/entrar"), "{\"login\":\"" + endereco + "\",\"senha\":\"" + S1 + "\"}"), 200));
        executar(post("/api/contas/eu/celular/codigo").header("Authorization", bearer), 202);
        executar(
                json(post("/api/contas/eu/celular/confirmar"), "{\"codigo\":\"" + codigo(celular) + "\"}")
                        .header("Authorization", bearer),
                204);

        // entrar só com o código por SMS
        executar(auth(post("/api/auth/codigo"), "{\"celular\":\"" + celular + "\"}"), 202);
        acesso(executar(
                auth(
                        post("/api/auth/entrar-com-codigo"),
                        "{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo(celular) + "\"}"),
                200));

        // esqueci e redefinir (pelo SMS: o e-mail ainda não foi confirmado)
        executar(auth(post("/api/auth/senha/esqueci"), "{\"login\":\"" + celular + "\"}"), 202);
        executar(
                auth(
                        post("/api/auth/senha/redefinir"),
                        "{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo(celular) + "\",\"novaSenha\":\"" + S2
                                + "\"}"),
                204);

        // trocar a senha logado
        bearer = acesso(executar(
                json(post("/api/auth/entrar"), "{\"login\":\"" + celular + "\",\"senha\":\"" + S2 + "\"}"), 200));
        executar(
                auth(post("/api/auth/senha/trocar"), "{\"senhaAtual\":\"" + S2 + "\",\"novaSenha\":\"" + S3 + "\"}")
                        .header("Authorization", bearer),
                204);

        // ligar o código no login e entrar com o segundo passo
        executar(post("/api/contas/eu/mfa/codigo").header("Authorization", bearer), 202);
        executar(
                json(post("/api/contas/eu/mfa"), "{\"ativo\":true,\"codigo\":\"" + codigo(celular) + "\"}")
                        .header("Authorization", bearer),
                204);
        String desafio = guardar(JsonPath.read(
                executar(
                                json(
                                        post("/api/auth/entrar"),
                                        "{\"login\":\"" + celular + "\",\"senha\":\"" + S3 + "\"}"),
                                403)
                        .getContentAsString(),
                "$.desafioId"));
        acesso(executar(
                auth(
                        post("/api/auth/segundo-passo"),
                        "{\"desafioId\":\"" + desafio + "\",\"codigo\":\"" + codigo(celular) + "\"}"),
                200));

        // prova de posse do celular e do e-mail de outra conta (não confirmados)
        Conta outra = contas.criar(Papel.CLIENTE);
        guardar(outra.celular());
        guardar(outra.email());
        executar(json(post("/api/contas/posse/celular"), "{\"celular\":\"" + outra.celular() + "\"}"), 202);
        guardar(JsonPath.read(
                executar(
                                json(
                                        post("/api/contas/posse/celular/confirmar"),
                                        "{\"celular\":\"" + outra.celular() + "\",\"codigo\":\""
                                                + codigo(outra.celular()) + "\"}"),
                                200)
                        .getContentAsString(),
                "$.comprovante"));
        executar(json(post("/api/contas/posse/email"), "{\"email\":\"" + outra.email() + "\"}"), 202);
        guardar(JsonPath.read(
                executar(
                                json(
                                        post("/api/contas/posse/email/confirmar"),
                                        "{\"token\":\"" + token(outra.email()) + "\"}"),
                                200)
                        .getContentAsString(),
                "$.comprovante"));

        // recolocar o e-mail numa conta que ficou sem
        Conta semEmail = contas.criar(Papel.CLIENTE);
        guardar(semEmail.celular());
        jdbc.update("UPDATE usuario SET email = NULL, email_verificado_em = NULL WHERE id = ?", semEmail.id());
        String novo = guardar("recolocado-" + System.nanoTime() + "@teste.coe.local");
        executar(
                json(put("/api/contas/eu/email"), "{\"email\":\"" + novo + "\"}")
                        .header("Authorization", bearer(semEmail.id(), Papel.CLIENTE)),
                202);
        executar(json(post("/api/contas/email/confirmar"), "{\"token\":\"" + token(novo) + "\"}"), 204);

        sms.aguardarEnvios();
        String log = saida.getAll();
        assertThat(log).contains("Login ok").contains("Enviado SMS");
        assertThat(segredos)
                .allSatisfy(segredo -> assertThat(log)
                        .as("o log não pode ter %s…", segredo.substring(0, 4))
                        .doesNotContain(segredo));
        assertThat(codigos)
                .hasSizeGreaterThanOrEqualTo(6)
                .allSatisfy(codigo -> assertThat(log)
                        .as("o log não pode ter um código de SMS")
                        .doesNotContainPattern("(?<![\\w-])" + Pattern.quote(codigo) + "(?![\\w-])"));
    }
}
