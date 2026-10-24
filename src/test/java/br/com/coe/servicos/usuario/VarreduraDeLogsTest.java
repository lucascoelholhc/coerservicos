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

    @Autowired
    private org.springframework.context.ApplicationContext contexto;

    @Autowired
    private br.com.coe.servicos.config.ConfiguracaoNegocio configuracao;

    @org.springframework.beans.factory.annotation.Value("${coe.gateway.token-webhook}")
    private String tokenDoWebhook;

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

    private static byte[] jpeg() throws java.io.IOException {
        java.awt.image.BufferedImage imagem =
                new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(imagem, "jpg", saida);
        return saida.toByteArray();
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

        // cadastro do profissional (DOM-02): CPF, chave Pix e nome original do arquivo nunca no log
        // (o CPF só entra com o celular confirmado: revisão de segurança da fatia 2)
        jdbc.update("UPDATE usuario SET celular_verificado_em = now() WHERE id = ?", semEmail.id());
        String autorizacao = bearer(semEmail.id(), Papel.CLIENTE);
        String cpf = guardar(
                br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste.unico().digitos());
        executar(
                json(
                                put("/api/profissional/cadastro/dados"),
                                "{\"cpf\":\"" + cpf + "\",\"dataNascimento\":\"1990-05-20\"}")
                        .header("Authorization", autorizacao),
                200);
        String pix = guardar("pix-" + System.nanoTime() + "@teste.coe.local");
        executar(
                json(put("/api/profissional/cadastro/pix"), "{\"tipo\":\"email\",\"chave\":\"" + pix + "\"}")
                        .header("Authorization", autorizacao),
                200);
        String nomeDoArquivo = guardar("rg-da-pessoa-" + System.nanoTime() + ".jpg");
        byte[] foto = jpeg();
        int status = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                                "/api/profissional/cadastro/documentos")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "arquivo", nomeDoArquivo, "image/jpeg", foto))
                        .param("tipo", "rg_frente")
                        .header("Authorization", autorizacao)
                        .with(requisicao -> {
                            requisicao.setContent(new byte[foto.length + 512]);
                            return requisicao;
                        }))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isEqualTo(201);

        // chat (DOM-05): o contato que alguém tenta passar antes do pagamento nunca vai para o log
        java.util.UUID perfil = br.com.coe.servicos.profissional.ProfissionaisDeTeste.ativo(contexto)
                .profissional();
        String conversa = JsonPath.read(
                executar(
                                json(post("/api/conversas"), "{\"profissionalId\":\"" + perfil + "\"}")
                                        .header("Authorization", autorizacao),
                                201)
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.id");
        String telefone = guardar("47 98877-" + (6000 + System.nanoTime() % 1000));
        guardar(telefone.replaceAll("\\D", ""));
        executar(
                json(post("/api/conversas/" + conversa + "/mensagens"), "{\"texto\":\"me liga no " + telefone + "\"}")
                        .header("Authorization", autorizacao),
                201);

        // contratação e pagamento (DOM-06/07): endereço, token do webhook e celular liberado nunca vão para o log
        java.time.LocalDate dia =
                br.com.coe.servicos.compartilhado.FusoDeNegocio.hoje(relogio).plusDays(1);
        while (dia.getDayOfWeek().getValue() > 5) {
            dia = dia.plusDays(1);
        }
        String rua = guardar("Rua Varredura "
                + Long.toString(System.nanoTime())
                        .chars()
                        .mapToObj(c -> String.valueOf((char) ('a' + c - '0')))
                        .collect(java.util.stream.Collectors.joining()));
        String contrato = JsonPath.read(
                executar(
                                json(
                                                post("/api/contratos"),
                                                ("{\"profissionalId\":\"%s\",\"dias\":[{\"data\":\"%s\",\"tipo\":\"inteira\"}],"
                                                                + "\"servicos\":[\"Corte de grama\"],\"material\":\"cliente_tem\","
                                                                + "\"endereco\":{\"cep\":\"88301000\",\"cidade\":4208203,"
                                                                + "\"logradouro\":\"%s\",\"numero\":\"77\"},"
                                                                + "\"versaoTermosAceita\":\"%s\"}")
                                                        .formatted(perfil, dia, rua, configuracao.versaoTermos()))
                                        .header("Authorization", autorizacao),
                                201)
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.id");
        String cobranca = JsonPath.read(
                executar(
                                json(post("/api/contratos/" + contrato + "/pagamento"), "{\"metodo\":\"pix\"}")
                                        .header("Authorization", autorizacao),
                                201)
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.cobrancaId");
        String externo = jdbc.queryForObject(
                "SELECT id_externo FROM cobranca WHERE id = ?", String.class, java.util.UUID.fromString(cobranca));
        java.math.BigDecimal total = jdbc.queryForObject(
                "SELECT valor_total FROM contrato WHERE id = ?",
                java.math.BigDecimal.class,
                java.util.UUID.fromString(contrato));
        String evento = ("{\"id\":\"evt_varredura_%d\",\"event\":\"PAYMENT_RECEIVED\","
                        + "\"payment\":{\"id\":\"%s\",\"value\":%s}}")
                .formatted(System.nanoTime(), externo, total.toPlainString());
        String tokenErrado = guardar("token-errado-" + System.nanoTime());
        executar(json(post("/api/webhooks/pagamento"), evento).header("asaas-access-token", tokenErrado), 401);
        executar(
                json(post("/api/webhooks/pagamento"), evento).header("asaas-access-token", guardar(tokenDoWebhook)),
                200);
        executar(get("/api/contratos/" + contrato).header("Authorization", autorizacao), 200);

        sms.aguardarEnvios();
        String log = saida.getAll();
        assertThat(log).contains("Login ok").contains("Enviado SMS").contains("Arquivo guardado");
        assertThat(segredos)
                .allSatisfy(segredo -> assertThat(log)
                        .as("o log não pode ter o segredo nº %d", segredos.indexOf(segredo))
                        .doesNotContain(segredo));
        assertThat(codigos)
                .hasSizeGreaterThanOrEqualTo(6)
                .allSatisfy(codigo -> assertThat(log)
                        .as("o log não pode ter um código de SMS")
                        .doesNotContainPattern("(?<![\\w-])" + Pattern.quote(codigo) + "(?![\\w-])"));
    }
}
