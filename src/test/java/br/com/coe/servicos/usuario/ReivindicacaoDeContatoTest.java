package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * RN61 (CORE-04): dado não confirmado não fica reservado. Quem prova a posse cria a conta com o
 * dado confirmado; a conta antiga perde o dado, as sessões e fica com contato pendente.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReivindicacaoDeContatoTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(70_000);
    private static final String SENHA = "Casa-Azul-2026";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private static String novoCelular() {
        return String.valueOf(47_900_000_000L + SEQUENCIA.incrementAndGet());
    }

    private static String novoEmail() {
        return "rn61-" + SEQUENCIA.incrementAndGet() + "@teste.coe.local";
    }

    private ResultActions cadastrar(String celular, String email, String comprovanteCelular, String comprovanteEmail)
            throws Exception {
        String extra = (comprovanteCelular == null ? "" : ",\"comprovanteCelular\":\"" + comprovanteCelular + "\"")
                + (comprovanteEmail == null ? "" : ",\"comprovanteEmail\":\"" + comprovanteEmail + "\"");
        return mockMvc.perform(post("/api/contas/cliente")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nova Pessoa\",\"celular\":\"" + celular + "\",\"email\":\"" + email
                        + "\",\"cep\":\"89010000\",\"senha\":\"" + SENHA + "\",\"versaoTermosAceita\":\"1.0\"" + extra
                        + "}"));
    }

    private String comprovanteDoCelular(String celular) throws Exception {
        mockMvc.perform(post("/api/contas/posse/celular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + celular + "\"}"))
                .andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(celular).orElseThrow();
        return JsonPath.read(
                mockMvc.perform(post("/api/contas/posse/celular/confirmar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"celular\":\"" + celular + "\",\"codigo\":\"" + codigo + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.comprovante");
    }

    private String comprovanteDoEmail(String endereco) throws Exception {
        mockMvc.perform(post("/api/contas/posse/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + endereco + "\"}"))
                .andExpect(status().isAccepted());
        String token = email.ultimoToken(endereco).orElseThrow();
        return JsonPath.read(
                mockMvc.perform(post("/api/contas/posse/email/confirmar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + token + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.comprovante");
    }

    private ResultActions entrarComSenha(String login) throws Exception {
        return mockMvc.perform(post("/api/auth/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"));
    }

    private String coluna(String coluna, UUID id) {
        return jdbc.queryForObject("SELECT " + coluna + "::text FROM usuario WHERE id = ?", String.class, id);
    }

    private UUID idDoCelular(String celular) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE celular = ?", UUID.class, celular);
    }

    // ---------------------------------------------------------------- cadastro sem comprovante

    @Test
    @DisplayName("cadastro com celular ou e-mail não confirmado de outra conta: 409 pode ser reivindicado")
    void naoConfirmadoPodeSerReivindicado() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);

        cadastrar(antiga.celular(), novoEmail(), null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-pode-ser-reivindicado"))
                .andExpect(jsonPath("$.campo").value("celular"));
        cadastrar(novoCelular(), antiga.email(), null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:email-pode-ser-reivindicado"))
                .andExpect(jsonPath("$.campo").value("email"));
    }

    @Test
    @DisplayName("dado confirmado: 409 já cadastrado de sempre, e a posse nem manda código")
    void confirmadoNuncaETomado() throws Exception {
        Conta antiga = contas.criarConfirmada(Papel.CLIENTE);

        cadastrar(antiga.celular(), novoEmail(), null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-cadastrado"));
        mockMvc.perform(post("/api/contas/posse/celular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + antiga.celular() + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(sms.para(antiga.celular())).isEmpty();
    }

    @Test
    @DisplayName("comprovante obtido antes, mas o dono confirmou no meio do caminho: 409 já cadastrado")
    void donoConfirmouNoMeio() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String comprovante = comprovanteDoCelular(antiga.celular());
        jdbc.update("UPDATE usuario SET celular_verificado_em = now() WHERE id = ?", antiga.id());

        cadastrar(antiga.celular(), novoEmail(), comprovante, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-cadastrado"));
        assertThat(coluna("celular", antiga.id())).isEqualTo(antiga.celular());
    }

    // ---------------------------------------------------------------- posse e transferência

    @Test
    @DisplayName("posse por SMS: a conta nova nasce com o celular confirmado; a antiga perde o celular e as sessões")
    void transferePorSms(CapturedOutput saida) throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        entrarComSenha(antiga.celular()).andExpect(status().isOk());
        String comprovante = comprovanteDoCelular(antiga.celular());

        cadastrar(antiga.celular(), novoEmail(), comprovante, null).andExpect(status().isCreated());

        UUID nova = idDoCelular(antiga.celular());
        assertThat(nova).isNotEqualTo(antiga.id());
        assertThat(coluna("celular_verificado_em", nova)).isNotNull();
        assertThat(coluna("celular", antiga.id())).isNull();
        assertThat(coluna("email", antiga.id())).isEqualTo(antiga.email());
        assertThat(jdbc.queryForList(
                        "SELECT motivo_revogacao FROM refresh_token WHERE usuario_id = ?", String.class, antiga.id()))
                .isNotEmpty()
                .containsOnly("contato_transferido");
        String auditoria = jdbc.queryForObject(
                "SELECT acao || ' ' || antes::text || ' ' || depois::text FROM log_auditoria WHERE entidade_id = ?",
                String.class,
                antiga.id());
        assertThat(auditoria).startsWith("contato.transferir").doesNotContain(antiga.celular());
        assertThat(saida.getAll()).doesNotContain(antiga.celular()).doesNotContain(comprovante);
    }

    @Test
    @DisplayName("posse por link: a conta nova nasce com o e-mail confirmado; a antiga perde o e-mail")
    void transferePorLink() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String comprovante = comprovanteDoEmail(antiga.email());

        cadastrar(novoCelular(), antiga.email(), null, comprovante).andExpect(status().isCreated());

        UUID nova = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, antiga.email());
        assertThat(coluna("email_verificado_em", nova)).isNotNull();
        assertThat(coluna("email", antiga.id())).isNull();
        assertThat(coluna("celular", antiga.id())).isEqualTo(antiga.celular());
    }

    @Test
    @DisplayName("perder só o e-mail não mexe no MFA da conta antiga")
    void perderEmailNaoMexeNoMfa() throws Exception {
        Conta antiga = contas.criar("ativo", true, Papel.CLIENTE);
        String comprovante = comprovanteDoEmail(antiga.email());

        cadastrar(novoCelular(), antiga.email(), null, comprovante).andExpect(status().isCreated());

        assertThat(coluna("mfa_sms_ativo", antiga.id())).isEqualTo("true");
    }

    @Test
    @DisplayName("posse silenciosa: dado livre não recebe nada, com o mesmo 202")
    void posseSilenciosa() throws Exception {
        String livre = novoCelular();
        String emailLivre = novoEmail();

        mockMvc.perform(post("/api/contas/posse/celular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + livre + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").exists());
        mockMvc.perform(post("/api/contas/posse/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailLivre + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(sms.para(livre)).isEmpty();
        assertThat(email.para(emailLivre)).isEmpty();
    }

    @Test
    @DisplayName(
            "conta antiga ficaria sem contato: 409 transferencia-indisponivel (só celular, ou os dois da mesma conta)")
    void transferenciaIndisponivel() throws Exception {
        String celular = novoCelular();
        UUID soCelular = jdbc.queryForObject(
                "INSERT INTO usuario (nome, celular, senha_hash) VALUES ('Só Celular', ?, 'hash') RETURNING id",
                UUID.class,
                celular);
        String comprovante = comprovanteDoCelular(celular);
        cadastrar(celular, novoEmail(), comprovante, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transferencia-indisponivel"));
        assertThat(coluna("celular", soCelular)).isEqualTo(celular);

        Conta antiga = contas.criar(Papel.CLIENTE);
        String doCelular = comprovanteDoCelular(antiga.celular());
        String doEmail = comprovanteDoEmail(antiga.email());
        cadastrar(antiga.celular(), antiga.email(), doCelular, doEmail)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transferencia-indisponivel"));
    }

    @Test
    @DisplayName("comprovante vencido (30 min), de outro destino ou reusado: recusado")
    void comprovanteInvalido() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String comprovante = comprovanteDoCelular(antiga.celular());
        relogio.avancar(Duration.ofMinutes(31));
        cadastrar(antiga.celular(), novoEmail(), comprovante, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:comprovante-invalido"));

        Conta outra = contas.criar(Papel.CLIENTE);
        String deOutroCelular = comprovanteDoCelular(outra.celular());
        cadastrar(antiga.celular(), novoEmail(), deOutroCelular, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:comprovante-invalido"));

        cadastrar(outra.celular(), novoEmail(), deOutroCelular, null).andExpect(status().isCreated());
        cadastrar(outra.celular(), novoEmail(), deOutroCelular, null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("corrida: o mesmo comprovante em dois cadastros ao mesmo tempo, só um vence")
    void corridaDoMesmoComprovante() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String comprovante = comprovanteDoCelular(antiga.celular());
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService duas = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = List.of(
                    duas.submit(() -> {
                        largada.await();
                        return cadastrar(antiga.celular(), novoEmail(), comprovante, null)
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }),
                    duas.submit(() -> {
                        largada.await();
                        return cadastrar(antiga.celular(), novoEmail(), comprovante, null)
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }));
            largada.countDown();
            List<Integer> status = List.of(
                    resultados.get(0).get(30, TimeUnit.SECONDS),
                    resultados.get(1).get(30, TimeUnit.SECONDS));

            assertThat(status).contains(201).containsOnlyOnce(201);
        } finally {
            duas.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, antiga.celular()))
                .isOne();
    }

    // ---------------------------------------------------------------- conta antiga: contato pendente

    @Test
    @DisplayName("a conta antiga entra pelo outro dado, com contato pendente: só o que está na lista de liberados")
    void contaAntigaFicaPendente() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        cadastrar(antiga.celular(), novoEmail(), comprovanteDoCelular(antiga.celular()), null)
                .andExpect(status().isCreated());

        String corpo = entrarComSenha(antiga.email())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.contatoPendente").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String bearer = "Bearer " + JsonPath.read(corpo, "$.accessToken");

        mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/contas/eu/mfa/codigo").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contato-pendente"));
    }

    @Test
    @DisplayName("revisão: token antigo (sem a marca) da conta antiga também é barrado, pelo banco")
    void tokenAntigoSemAMarca() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String tokenDeAntes = bearer(antiga.id(), Papel.CLIENTE);
        cadastrar(antiga.celular(), novoEmail(), comprovanteDoCelular(antiga.celular()), null)
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/contas/eu/mfa/codigo").header("Authorization", tokenDeAntes))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contato-pendente"));
        mockMvc.perform(get("/api/contas/eu").header("Authorization", tokenDeAntes))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("revisão: contato de conta ADMIN nunca é transferido (409 transferencia-indisponivel)")
    void adminNaoPerdeContato() throws Exception {
        Conta admin = contas.criar(Papel.ADMIN);
        String comprovante = comprovanteDoEmail(admin.email());

        cadastrar(novoCelular(), admin.email(), null, comprovante)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transferencia-indisponivel"));
        assertThat(coluna("email", admin.id())).isEqualTo(admin.email());
    }

    @Test
    @DisplayName("revisão: PUT em dois números seguidos: só o último código vale (nada de dois ativos)")
    void putEmDoisNumeros() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String bearer = loginPendente(antiga);
        String primeiro = novoCelular();
        String segundo = novoCelular();

        colocarCelular(bearer, primeiro).andExpect(status().isAccepted());
        String codigoDoPrimeiro = sms.ultimoCodigo(primeiro).orElseThrow();
        colocarCelular(bearer, segundo).andExpect(status().isAccepted());

        confirmarCelular(bearer, codigoDoPrimeiro).andExpect(status().isUnprocessableEntity());
        confirmarCelular(bearer, sms.ultimoCodigo(segundo).orElseThrow()).andExpect(status().isNoContent());
        assertThat(coluna("celular", antiga.id())).isEqualTo(segundo);
    }

    // ---------------------------------------------------------------- recolocar o dado perdido (opção 1)

    private String loginPendente(Conta antiga) throws Exception {
        cadastrar(antiga.celular(), novoEmail(), comprovanteDoCelular(antiga.celular()), null)
                .andExpect(status().isCreated());
        return "Bearer "
                + JsonPath.read(
                        entrarComSenha(antiga.email()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions colocarCelular(String bearer, String celular) throws Exception {
        return mockMvc.perform(put("/api/contas/eu/celular")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\"}"));
    }

    private ResultActions confirmarCelular(String bearer, String codigo) throws Exception {
        return mockMvc.perform(post("/api/contas/eu/celular/confirmar")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\"}"));
    }

    @Test
    @DisplayName("recolocar o celular: o PUT só manda o código; a confirmação grava o número do PUT, já confirmado")
    void recolocaCelular() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String bearer = loginPendente(antiga);
        String novo = novoCelular();

        colocarCelular(bearer, novo).andExpect(status().isAccepted());
        assertThat(coluna("celular", antiga.id())).as("o PUT não grava").isNull();
        confirmarCelular(bearer, sms.ultimoCodigo(novo).orElseThrow()).andExpect(status().isNoContent());

        assertThat(coluna("celular", antiga.id())).isEqualTo(novo);
        assertThat(coluna("celular_verificado_em", antiga.id())).isNotNull();
        entrarComSenha(antiga.email())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.contatoPendente").value(false));
    }

    @Test
    @DisplayName("PUT com o campo preenchido ou com número de outra conta: 409")
    void putRecusado() throws Exception {
        Conta normal = contas.criar(Papel.CLIENTE);
        colocarCelular(bearer(normal.id(), Papel.CLIENTE), novoCelular())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-preenchido"));

        Conta antiga = contas.criar(Papel.CLIENTE);
        String bearer = loginPendente(antiga);
        colocarCelular(bearer, normal.celular())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-cadastrado"));
    }

    @Test
    @DisplayName("o número foi tomado entre o PUT e a confirmação: 409 na confirmação, nada gravado")
    void tomadoAntesDeConfirmar() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String bearer = loginPendente(antiga);
        String novo = novoCelular();
        colocarCelular(bearer, novo).andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(novo).orElseThrow();

        cadastrar(novo, novoEmail(), null, null).andExpect(status().isCreated());

        confirmarCelular(bearer, codigo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-ja-cadastrado"));
        assertThat(coluna("celular", antiga.id())).isNull();
    }

    @Test
    @DisplayName("corrida: confirmar o número do PUT e outro cadastro com o mesmo número ao mesmo tempo, só um vence")
    void corridaPutECadastro() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        String bearer = loginPendente(antiga);
        String novo = novoCelular();
        colocarCelular(bearer, novo).andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(novo).orElseThrow();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService duas = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> confirmacao = duas.submit(() -> {
                largada.await();
                return confirmarCelular(bearer, codigo)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> cadastro = duas.submit(() -> {
                largada.await();
                return cadastrar(novo, novoEmail(), null, null)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            largada.countDown();
            int a = confirmacao.get(30, TimeUnit.SECONDS);
            int b = cadastro.get(30, TimeUnit.SECONDS);

            assertThat(List.of(a, b)).containsOnlyOnce(409).anyMatch(s -> s == 204 || s == 201);
        } finally {
            duas.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, novo))
                .isOne();
    }

    @Test
    @DisplayName("recolocar o e-mail: o link vai para o endereço do PUT e a confirmação grava esse endereço")
    void recolocaEmail() throws Exception {
        Conta antiga = contas.criar(Papel.CLIENTE);
        cadastrar(novoCelular(), antiga.email(), null, comprovanteDoEmail(antiga.email()))
                .andExpect(status().isCreated());
        String bearer = "Bearer "
                + JsonPath.read(
                        entrarComSenha(antiga.celular())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.accessToken");
        String novo = novoEmail();

        mockMvc.perform(put("/api/contas/eu/email")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + novo + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(coluna("email", antiga.id())).isNull();
        mockMvc.perform(post("/api/contas/email/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + email.ultimoToken(novo).orElseThrow() + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(coluna("email", antiga.id())).isEqualTo(novo);
        assertThat(coluna("email_verificado_em", antiga.id())).isNotNull();
    }
}
