package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.cripto.HmacDeCpf;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;
import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DOM-02: cadastro do profissional em etapas, só do próprio rascunho, com as regras do RN08 a RN10. */
class CadastroProfissionalTest extends IntegracaoTest {

    private static final String BASE = CadastroDeTeste.BASE;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private HmacDeCpf hmac;

    private ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private CadastroDeTeste cadastroDe(UUID usuario) {
        return new CadastroDeTeste(mockMvc, bearer(usuario, Papel.CLIENTE));
    }

    private static String dados(String cpf, String nascimento) {
        return "{\"cpf\":\"" + cpf + "\",\"dataNascimento\":\"" + nascimento + "\"}";
    }

    @Test
    @DisplayName("sem rascunho: 404 cadastro-nao-iniciado; sem login: 401")
    void semRascunho() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();

        mockMvc.perform(get(BASE).header("Authorization", bearer(usuario, Papel.CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-encontrado"))
                .andExpect(jsonPath("$.detail").value("Você ainda não começou o cadastro."));
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a primeira etapa cria o rascunho; nome vem da conta; etapas completas no GET")
    void primeiraEtapa() throws Exception {
        ContasDeTeste.Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        CadastroDeTeste cadastro = cadastroDe(conta.id());

        cadastro.etapa("trabalho", """
                        {"profissao":"pedreiro","outras":["pintor"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rascunho"))
                .andExpect(jsonPath("$.nome").value(conta.nome()))
                .andExpect(jsonPath("$.profissao").value("pedreiro"))
                .andExpect(jsonPath("$.outrasProfissoes[0]").value("pintor"))
                .andExpect(jsonPath("$.etapas.trabalho").value(true))
                .andExpect(jsonPath("$.etapas.dados").value(false))
                .andExpect(jsonPath("$.etapaAtual").value(2));
        mockMvc.perform(get(BASE).header("Authorization", bearer(conta.id(), Papel.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissao").value("pedreiro"));
        // o papel PROFISSIONAL só vem na aprovação (DOM-03)
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario_papel WHERE usuario_id = ? AND papel = 'PROFISSIONAL'",
                        Integer.class,
                        conta.id()))
                .isZero();
    }

    @Test
    @DisplayName("profissão inativa (em breve) ou desconhecida: 422 profissao-invalida")
    void profissaoInvalida() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());

        cadastro.etapa("trabalho", """
                        {"profissao":"encanador","outras":[]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:profissao-invalida"));
        cadastro.etapa("trabalho", """
                        {"profissao":"pedreiro","outras":["astronauta"]}""").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("idade mínima: 17 anos e 364 dias recusado; exatos 18 anos aceito (Clock)")
    void idadeMinima() throws Exception {
        relogio.fixarEm(Instant.parse("2026-06-15T12:00:00Z"));
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());
        Cpf cpf = CpfsDeTeste.unico();

        cadastro.etapa("dados", dados(cpf.digitos(), "2008-06-16"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:idade-minima"))
                .andExpect(jsonPath("$.detail").value("Para trabalhar na COE é preciso ter 18 anos ou mais."));
        cadastro.etapa("dados", dados(cpf.digitos(), "2008-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapas.dados").value(true));
    }

    @Test
    @DisplayName("CPF: dígito errado = 422; cifrado e com HMAC no banco; resposta só mascarada")
    void cpf() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        Cpf cpf = CpfsDeTeste.unico();

        cadastro.etapa("dados", dados("529.982.247-24", "1990-05-20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cpf-invalido"));
        cadastro.etapa("dados", dados(cpf.formatado(), "1990-05-20"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.cpf").value("***.***.***-" + cpf.digitos().substring(9)))
                .andExpect(content().string(not(containsString(cpf.digitos()))));

        byte[] cifrado =
                jdbc.queryForObject("SELECT cpf_cifrado FROM profissional WHERE usuario_id = ?", byte[].class, usuario);
        byte[] hash =
                jdbc.queryForObject("SELECT cpf_hash FROM profissional WHERE usuario_id = ?", byte[].class, usuario);
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(cpf.digitos());
        assertThat(hash).isEqualTo(hmac.de(cpf));
    }

    @Test
    @DisplayName("CPF de outro cadastro: 409 neutro (não diz de quem é) e auditoria sem o CPF")
    void cpfRepetido() throws Exception {
        Cpf cpf = CpfsDeTeste.unico();
        cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id())
                .etapa("dados", dados(cpf.digitos(), "1990-05-20"))
                .andExpect(status().isOk());
        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();

        String corpo = cadastroDe(outro)
                .etapa("dados", dados(cpf.formatado(), "1990-05-20"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cpf-em-uso"))
                .andExpect(jsonPath("$.campo").value("cpf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(corpo).doesNotContain(cpf.digitos()).doesNotContain(cpf.formatado());
        String auditoria = jdbc.queryForObject(
                "SELECT coalesce(antes::text, '') || coalesce(depois::text, '') FROM log_auditoria"
                        + " WHERE ator_id = ? AND acao = 'profissional.cpf-repetido'",
                String.class,
                outro);
        assertThat(auditoria).doesNotContain(cpf.digitos());
    }

    @Test
    @DisplayName("serviço fora das profissões escolhidas ou inexistente: 422 servico-invalido")
    void servicoInvalido() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());
        cadastro.etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""").andExpect(status().isOk());

        cadastro.etapa("servicos", """
                        {"servicos":[{"profissao":"pintor","nome":"Pintura interna"}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:servico-invalido"));
        cadastro.etapa("servicos", """
                        {"servicos":[{"profissao":"pedreiro","nome":"Voar"}]}""").andExpect(status().isUnprocessableEntity());
        cadastro.etapa("servicos", """
                        {"servicos":[{"profissao":"pedreiro","nome":"Alvenaria"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicos[0].nome").value("Alvenaria"));
    }

    @Test
    @DisplayName("valor: abaixo do mínimo da configuração = 422; meia diária fora da faixa = 422")
    void valor() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());

        cadastro.etapa("valor", """
                        {"valorDiaria":"79.99","horario":"7h às 17h"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:valor-abaixo-do-minimo"))
                .andExpect(jsonPath("$.detail").value(containsString("R$ 80,00")));
        cadastro.etapa("valor", """
                        {"valorDiaria":"200.00","valorMeiaDiaria":"200.00","horario":"7h às 17h"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:meia-diaria-invalida"));
        cadastro.etapa("valor", """
                        {"valorDiaria":"200.00","valorMeiaDiaria":"49.00","horario":"7h às 17h"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:meia-diaria-invalida"));
        cadastro.etapa("valor", """
                        {"valorDiaria":"280.00","valorMeiaDiaria":"150.00","horario":"7h às 17h"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDiaria").value("280.00"))
                .andExpect(jsonPath("$.valorMeiaDiaria").value("150.00"));
    }

    @Test
    @DisplayName("onde: cidade fora do catálogo ativo = 422; raio fora de 5/10/20/40 = 400")
    void onde() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());

        cadastro.etapa("onde", """
                        {"cidade":1234567,"raioKm":20,"outrasCidades":[]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cidade-invalida"));
        cadastro.etapa("onde", """
                        {"cidade":4202404,"raioKm":15,"outrasCidades":[]}""").andExpect(status().isBadRequest());
        cadastro.etapa("onde", """
                        {"cidade":4202404,"raioKm":10,"outrasCidades":[4208203]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value(4202404))
                .andExpect(jsonPath("$.outrasCidades[0]").value(4208203));
    }

    @Test
    @DisplayName("sobre: telefone = 422 texto-com-contato; curto = 422 sobre-curto; CEP passa")
    void sobre() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());

        cadastro.etapa("sobre", """
                        {"frases":[],"texto":"Trabalho bem. Me chama no 47 99999-0001."}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        cadastro.etapa("sobre", """
                        {"frases":["Moro perto, ligue 4799999 0001"],"texto":null}""").andExpect(status().isUnprocessableEntity());
        cadastro.etapa("sobre", """
                        {"frases":[],"texto":"Sou bom."}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:sobre-curto"));
        cadastro.etapa("sobre", """
                        {"frases":["Sou caprichoso."],"texto":"Atendo perto do CEP 89010-000 há 12 anos."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapas.sobre").value(true));
    }

    @Test
    @DisplayName("Pix: chave que não combina com o tipo = 422 pix-invalido; resposta sem a chave inteira")
    void pix() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());

        cadastro.etapa("pix", """
                        {"tipo":"email","chave":"nao-e-email"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:pix-invalido"));
        cadastro.etapa("pix", """
                        {"tipo":"celular","chave":"123"}""").andExpect(status().isUnprocessableEntity());
        cadastro.etapa("pix", """
                        {"tipo":"email","chave":"pessoa.pix@exemplo.com"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chavePixTipo").value("email"))
                .andExpect(content().string(not(containsString("pessoa.pix@exemplo.com"))));
    }

    @Test
    @DisplayName("envio incompleto: 422 cadastro-incompleto com o que falta")
    void envioIncompleto() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(usuario).etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""").andExpect(status().isOk());

        enviar(usuario, "1.0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cadastro-incompleto"))
                .andExpect(jsonPath("$.detail").value(containsString("documento")));
    }

    @Test
    @DisplayName("CPF só com celular confirmado: sem ele, 422 antes de olhar o CPF (nem o 409 de CPF em uso)")
    void dadosSemCelularConfirmado() throws Exception {
        Cpf cpf = CpfsDeTeste.unico();
        UUID dono = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(dono).etapa("dados", dados(cpf.digitos(), "1990-05-20")).andExpect(status().isOk());
        UUID outro = contas.criar(Papel.CLIENTE).id();

        cadastroDe(outro)
                .etapa("dados", dados(cpf.digitos(), "1990-05-20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-nao-confirmado"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE ator_id = ? AND acao = 'profissional.cpf-repetido'",
                        Integer.class,
                        outro))
                .isZero();
    }

    @Test
    @DisplayName("envio sem celular confirmado (RN08): 422 celular-nao-confirmado")
    void envioSemCelular() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(usuario).completo();
        // o celular saiu da conta depois dos dados (ex.: transferido pela RN61): o envio confere de novo
        jdbc.update("UPDATE usuario SET celular_verificado_em = NULL WHERE id = ?", usuario);

        enviar(usuario, "1.0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:celular-nao-confirmado"));
    }

    @Test
    @DisplayName("envio completo: em análise, fora da busca, auditado; editar depois = 422 cadastro-em-analise")
    void envioCompleto() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.completo();

        enviar(usuario, "1.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("em_analise"));

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM profissional WHERE usuario_id = ? AND enviado_analise_em IS NOT NULL",
                        String.class,
                        usuario))
                .isEqualTo("em_analise");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE ator_id = ? AND acao = 'profissional.enviado-analise'",
                        Integer.class,
                        usuario))
                .isEqualTo(1);
        cadastro.etapa("dias", """
                        {"dias":[6]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cadastro-em-analise"));
        enviar(usuario, "1.0").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("envio com termos de outra versão: 422 termos-desatualizados")
    void termosDesatualizados() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        cadastroDe(usuario).completo();

        enviar(usuario, "0.9")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:termos-desatualizados"));
    }

    @Test
    @DisplayName("duas primeiras etapas ao mesmo tempo (duplo clique): nenhuma dá 500, fica um rascunho só")
    void corridaNoPrimeiroRascunho() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        java.util.concurrent.ExecutorService duas = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            respostas.add(duas.submit(() -> {
                largada.await();
                return cadastroDe(usuario)
                        .etapa("trabalho", """
                                {"profissao":"pedreiro","outras":[]}""")
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        largada.countDown();
        for (java.util.concurrent.Future<Integer> resposta : respostas) {
            assertThat(resposta.get()).isIn(200, 409);
        }
        duas.shutdown();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM profissional WHERE usuario_id = ?", Integer.class, usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("em análise, mudar o CPF dá 422 cadastro-em-analise (não o 409 do CPF)")
    void dadosEmAnalise() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.completo();
        enviar(usuario, "1.0").andExpect(status().isOk());

        cadastro.etapa("dados", dados(CpfsDeTeste.unico().digitos(), "1990-05-20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:cadastro-em-analise"));
    }

    @Test
    @DisplayName("trocar a profissão tira os serviços da profissão antiga")
    void trocarProfissao() throws Exception {
        CadastroDeTeste cadastro =
                cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id());
        cadastro.etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""");
        cadastro.etapa("servicos", """
                {"servicos":[{"profissao":"pedreiro","nome":"Alvenaria"}]}""");

        cadastro.etapa("trabalho", """
                        {"profissao":"pintor","outras":[]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicos").isEmpty())
                .andExpect(jsonPath("$.etapas.servicos").value(false));
    }

    @Test
    @DisplayName("faixa de experiência fora da lista: 422 experiencia-invalida")
    void experienciaInvalida() throws Exception {
        cadastroDe(contas.criarConfirmada(Papel.CLIENTE).id())
                .etapa("experiencia", """
                        {"anos":7,"ferramentas":"sim"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:experiencia-invalida"));
    }

    @Test
    @DisplayName("telefone partido entre os textos (frase + texto) é pego no envio")
    void contatoPartidoEntreCampos() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        cadastro.completo();
        cadastro.etapa("sobre", """
                        {"frases":["Atendo bem, contato 47 9999"],"texto":"0001 é o final do número do trabalho."}""").andExpect(status().isOk());

        enviar(usuario, "1.0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
    }

    @Test
    @DisplayName("trocar o Pix e os dados pessoais fica na auditoria (só o tipo, nunca o valor)")
    void auditoriaDePixEDados() throws Exception {
        UUID usuario = contas.criarConfirmada(Papel.CLIENTE).id();
        CadastroDeTeste cadastro = cadastroDe(usuario);
        Cpf cpf = CpfsDeTeste.unico();
        cadastro.etapa("dados", dados(cpf.digitos(), "1990-05-20")).andExpect(status().isOk());
        cadastro.etapa("pix", """
                {"tipo":"email","chave":"auditoria.pix@exemplo.com"}""").andExpect(status().isOk());

        String auditoria = jdbc.queryForObject(
                "SELECT string_agg(acao || coalesce(depois::text, ''), ' ') FROM log_auditoria WHERE ator_id = ?",
                String.class,
                usuario);
        assertThat(auditoria)
                .contains("profissional.dados-alterados")
                .contains("profissional.pix-alterado")
                .contains("email")
                .doesNotContain("auditoria.pix@exemplo.com")
                .doesNotContain(cpf.digitos());
    }

    private ResultActions enviar(UUID usuario, String versaoTermos) throws Exception {
        return mockMvc.perform(post(BASE + "/envio")
                .header("Authorization", bearer(usuario, Papel.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versaoTermosAceita\":\"" + versaoTermos + "\",\"aceitoRegraDeContato\":true}"));
    }
}
