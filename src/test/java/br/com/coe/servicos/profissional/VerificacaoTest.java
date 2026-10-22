package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
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
import br.com.coe.servicos.compartilhado.pessoal.Cpf;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DOM-03: verificação do cadastro pelo admin (RN11 a RN14, RN16, RF49). */
@ExtendWith(OutputCaptureExtension.class)
class VerificacaoTest extends IntegracaoTest {

    private static final String BASE = "/api/admin/verificacao";
    private static final String TODOS_OS_ITENS = """
            {"itens":{"cpf":true,"documento":true,"selfie":true,"fotos":true,"texto":true}}""";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private ContasDeTeste contas;

    @BeforeEach
    void criarContas() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    /** Um profissional com o cadastro completo e enviado para análise. */
    private record Enviado(UUID usuario, String celular, UUID profissional, Cpf cpf, CadastroDeTeste cadastro) {}

    private Enviado enviado() throws Exception {
        return enviado(contas.criarConfirmada(Papel.CLIENTE), false);
    }

    private Enviado enviado(ContasDeTeste.Conta conta, boolean comNr10) throws Exception {
        CadastroDeTeste cadastro = new CadastroDeTeste(mockMvc, bearer(conta.id(), Papel.CLIENTE));
        Cpf cpf = cadastro.completo();
        if (comNr10) {
            cadastro.etapa("trabalho", """
                            {"profissao":"pedreiro","outras":["eletricista"]}""").andExpect(status().isOk());
            cadastro.documento("certificado_nr10").andExpect(status().isCreated());
        }
        enviar(conta.id()).andExpect(status().isOk());
        UUID profissional =
                jdbc.queryForObject("SELECT id FROM profissional WHERE usuario_id = ?", UUID.class, conta.id());
        return new Enviado(conta.id(), conta.celular(), profissional, cpf, cadastro);
    }

    private ResultActions enviar(UUID usuario) throws Exception {
        return mockMvc.perform(post(CadastroDeTeste.BASE + "/envio")
                .header("Authorization", bearer(usuario, Papel.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"versaoTermosAceita":"1.0","aceitoRegraDeContato":true}"""));
    }

    private UUID admin() {
        return contas.criarConfirmada(Papel.ADMIN).id();
    }

    private ResultActions decidir(UUID admin, UUID profissional, String acao, String json) throws Exception {
        return mockMvc.perform(post(BASE + "/" + profissional + "/" + acao)
                .header("Authorization", bearer(admin, Papel.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String comNr10(String decisao) {
        return "{\"itens\":{\"cpf\":true,\"documento\":true,\"selfie\":true,\"fotos\":true,\"texto\":true}," + decisao
                + "}";
    }

    private String statusDe(UUID profissional) {
        return jdbc.queryForObject("SELECT status FROM profissional WHERE id = ?", String.class, profissional);
    }

    private String nr10De(UUID profissional) {
        return jdbc.queryForObject("SELECT nr10_status FROM profissional WHERE id = ?", String.class, profissional);
    }

    private List<String> papeisDe(UUID usuario) {
        return jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, usuario);
    }

    private List<String> acoesDe(UUID ator, UUID entidade) {
        return jdbc.queryForList(
                "SELECT acao FROM log_auditoria WHERE ator_id = ? AND entidade_id = ?", String.class, ator, entidade);
    }

    private List<String> smsPara(String celular) {
        sms.aguardarEnvios();
        return sms.para(celular).stream().map(mensagem -> mensagem.texto()).toList();
    }

    @Test
    @DisplayName("fila: o cadastro enviado aparece para o ADMIN, mais antigo primeiro; CLIENTE 403 e sem token 401")
    void fila() throws Exception {
        Enviado primeiro = enviado();
        relogio.avancar(Duration.ofMinutes(1));
        Enviado segundo = enviado();
        UUID admin = admin();

        List<String> ids = new java.util.ArrayList<>();
        StringBuilder corpo = new StringBuilder();
        boolean temMais = true;
        for (int pagina = 0; temMais && pagina < 50; pagina++) {
            String resposta = mockMvc.perform(get(BASE)
                            .param("pagina", String.valueOf(pagina))
                            .header("Authorization", bearer(admin, Papel.ADMIN)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            ids.addAll(JsonPath.read(resposta, "$.itens[*].id"));
            temMais = JsonPath.read(resposta, "$.temMais");
            corpo.append(resposta);
        }
        assertThat(ids)
                .contains(
                        primeiro.profissional().toString(),
                        segundo.profissional().toString());
        assertThat(ids.indexOf(primeiro.profissional().toString()))
                .isLessThan(ids.indexOf(segundo.profissional().toString()));
        assertThat(corpo.toString()).doesNotContain(primeiro.cpf().digitos()).doesNotContain(primeiro.celular());

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(primeiro.usuario(), Papel.CLIENTE, Papel.PROFISSIONAL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("detalhe: CPF completo para conferir com o documento, Pix mascarado, sem chave de arquivo; auditado")
    void detalhe() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        String corpo = mockMvc.perform(
                        get(BASE + "/" + enviado.profissional()).header("Authorization", bearer(admin, Papel.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(enviado.cpf().formatado()))
                .andExpect(jsonPath("$.celularConfirmado").value(true))
                .andExpect(jsonPath("$.cadastro.status").value("em_analise"))
                .andExpect(jsonPath("$.cadastro.documentos.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(corpo).doesNotContain("123e4567-e89b-12d3-a456-426614174000").doesNotContain("documento/");
        assertThat(acoesDe(admin, enviado.profissional())).contains("profissional.verificacao-vista");
        assertThat(jdbc.queryForObject(
                        "SELECT string_agg(coalesce(depois::text, ''), ' ') FROM log_auditoria WHERE ator_id = ?",
                        String.class,
                        admin))
                .doesNotContain(enviado.cpf().digitos());
    }

    @Test
    @DisplayName("detalhe de cadastro que não existe: 404")
    void detalheInexistente() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()).header("Authorization", bearer(admin(), Papel.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("URL do documento para o ADMIN: assinada, curta e auditada; documento de outro cadastro = 404")
    void urlDoDocumento() throws Exception {
        Enviado enviado = enviado();
        Enviado outro = enviado();
        UUID admin = admin();
        UUID documento = jdbc.queryForObject(
                "SELECT id FROM documento_verificacao WHERE profissional_id = ? AND tipo = 'selfie'",
                UUID.class,
                enviado.profissional());

        mockMvc.perform(get(BASE + "/" + enviado.profissional() + "/documentos/" + documento + "/url")
                        .header("Authorization", bearer(admin, Papel.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(Matchers.containsString("X-Amz-Expires=120")));
        mockMvc.perform(get(BASE + "/" + outro.profissional() + "/documentos/" + documento + "/url")
                        .header("Authorization", bearer(admin, Papel.ADMIN)))
                .andExpect(status().isNotFound());
        assertThat(acoesDe(admin, documento)).contains("profissional.documento-visto-admin");
    }

    @Test
    @DisplayName("URL da foto do portfólio para o ADMIN conferir os trabalhos (RN12)")
    void urlDaFoto() throws Exception {
        Enviado enviado = enviado();
        UUID foto = jdbc.queryForObject(
                "SELECT id FROM foto_portfolio WHERE profissional_id = ?", UUID.class, enviado.profissional());

        mockMvc.perform(get(BASE + "/" + enviado.profissional() + "/fotos/" + foto + "/url")
                        .header("Authorization", bearer(admin(), Papel.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(Matchers.containsString("X-Amz-Signature")));
    }

    @Test
    @DisplayName("aprovar sem todos os itens do checklist: 422 checklist-incompleto e continua em análise (RN13)")
    void aprovarSemChecklist() throws Exception {
        Enviado enviado = enviado();

        decidir(admin(), enviado.profissional(), "aprovar", """
                        {"itens":{"cpf":true,"documento":true,"selfie":false,"fotos":true,"texto":true}}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:checklist-incompleto"));
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
    }

    @Test
    @DisplayName("aprovar: vira ativo, ganha o papel PROFISSIONAL, documentos aprovados, SMS e auditoria")
    void aprovar() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "aprovar", TODOS_OS_ITENS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativo"));

        assertThat(statusDe(enviado.profissional())).isEqualTo("ativo");
        assertThat(jdbc.queryForObject(
                        "SELECT aprovado_por FROM profissional WHERE id = ?", UUID.class, enviado.profissional()))
                .isEqualTo(admin);
        assertThat(papeisDe(enviado.usuario())).contains("CLIENTE", "PROFISSIONAL");
        assertThat(jdbc.queryForList(
                        "SELECT DISTINCT status FROM documento_verificacao WHERE profissional_id = ?",
                        String.class,
                        enviado.profissional()))
                .containsExactly("aprovado");
        assertThat(smsPara(enviado.celular())).anyMatch(texto -> texto.contains("aprovado"));
        assertThat(acoesDe(admin, enviado.profissional())).contains("profissional.aprovar");
    }

    @Test
    @DisplayName("decidir duas vezes: a segunda dá 409 verificacao-indisponivel (outro admin já decidiu)")
    void decidirDuasVezes() throws Exception {
        Enviado enviado = enviado();
        decidir(admin(), enviado.profissional(), "aprovar", TODOS_OS_ITENS).andExpect(status().isOk());

        decidir(admin(), enviado.profissional(), "recusar", """
                        {"motivo":"Documento ilegível."}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:verificacao-indisponivel"));
        assertThat(statusDe(enviado.profissional())).isEqualTo("ativo");
    }

    @Test
    @DisplayName("pedir correção: o motivo vai por SMS, o profissional corrige e reenvia para análise")
    void pedirCorrecao() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "correcao", """
                        {"motivo":"A selfie está escura. Tire outra com mais luz."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("correcao_pedida"));

        assertThat(statusDe(enviado.profissional())).isEqualTo("correcao_pedida");
        assertThat(smsPara(enviado.celular())).anyMatch(texto -> texto.contains("A selfie está escura"));
        assertThat(acoesDe(admin, enviado.profissional())).contains("profissional.pedir-correcao");
        // a tela do cadastro mostra o que corrigir (o mesmo texto do SMS, já passado pela censura)
        String dono = bearer(enviado.usuario(), Papel.CLIENTE);
        mockMvc.perform(get(CadastroDeTeste.BASE).header("Authorization", dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoCorrecao").value("A selfie está escura. Tire outra com mais luz."));
        enviado.cadastro().documento("selfie").andExpect(status().isCreated());
        enviar(enviado.usuario()).andExpect(status().isOk());
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
        mockMvc.perform(get(CadastroDeTeste.BASE).header("Authorization", dono))
                .andExpect(jsonPath("$.motivoCorrecao").value(org.hamcrest.Matchers.nullValue()));
        // fora da correção pedida o motivo nunca sai, mesmo se ficasse gravado na linha
        jdbc.update("UPDATE profissional SET motivo_correcao = 'antigo' WHERE id = ?", enviado.profissional());
        mockMvc.perform(get(CadastroDeTeste.BASE).header("Authorization", dono))
                .andExpect(jsonPath("$.status").value("em_analise"))
                .andExpect(jsonPath("$.motivoCorrecao").value(org.hamcrest.Matchers.nullValue()));
        assertThat(papeisDe(enviado.usuario())).doesNotContain("PROFISSIONAL");
    }

    @Test
    @DisplayName("recusar: fica recusado com o motivo, SMS e auditoria; o cadastro não muda mais")
    void recusar() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "recusar", """
                        {"motivo":"O documento não é da mesma pessoa da selfie."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recusado"));

        assertThat(jdbc.queryForObject(
                        "SELECT motivo_recusa FROM profissional WHERE id = ?", String.class, enviado.profissional()))
                .isEqualTo("O documento não é da mesma pessoa da selfie.");
        assertThat(smsPara(enviado.celular())).anyMatch(texto -> texto.contains("não foi aprovado"));
        assertThat(acoesDe(admin, enviado.profissional())).contains("profissional.recusar");
        enviado.cadastro().etapa("dias", """
                        {"dias":[1,2]}""").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("correção e recusa sem motivo: 400")
    void semMotivo() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "recusar", """
                        {"motivo":"  "}""").andExpect(status().isBadRequest());
        decidir(admin, enviado.profissional(), "correcao", "{}").andExpect(status().isBadRequest());
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
    }

    @Test
    @DisplayName("NR-10: com certificado, aprovar exige a decisão do NR-10; verificado dá o selo (RN14)")
    void nr10() throws Exception {
        Enviado enviado = enviado(contas.criarConfirmada(Papel.CLIENTE), true);
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "aprovar", TODOS_OS_ITENS)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nr10-sem-decisao"));
        decidir(admin, enviado.profissional(), "aprovar", comNr10("\"nr10\":\"verificado\""))
                .andExpect(status().isOk());
        assertThat(nr10De(enviado.profissional())).isEqualTo("verificado");
    }

    @Test
    @DisplayName("NR-10 recusado: aprova sem o selo, com motivo; sem certificado, mandar NR-10 = 422")
    void nr10Recusado() throws Exception {
        Enviado comCertificado = enviado(contas.criarConfirmada(Papel.CLIENTE), true);
        Enviado semCertificado = enviado();
        UUID admin = admin();

        decidir(admin, semCertificado.profissional(), "aprovar", comNr10("\"nr10\":\"verificado\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nr10-sem-certificado"));
        decidir(admin, comCertificado.profissional(), "aprovar", comNr10("\"nr10\":\"recusado\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nr10-sem-motivo"));
        decidir(
                        admin,
                        comCertificado.profissional(),
                        "aprovar",
                        comNr10("\"nr10\":\"recusado\",\"motivoNr10\":\"Certificado vencido.\""))
                .andExpect(status().isOk());
        assertThat(nr10De(comCertificado.profissional())).isEqualTo("recusado");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM documento_verificacao WHERE profissional_id = ? AND tipo = 'certificado_nr10'",
                        String.class,
                        comCertificado.profissional()))
                .isEqualTo("recusado");
    }

    @Test
    @DisplayName("o admin não verifica o próprio cadastro: 403 verificacao-propria")
    void verificacaoPropria() throws Exception {
        ContasDeTeste.Conta conta = contas.criarConfirmada(Papel.ADMIN);
        Enviado enviado = enviado(conta, false);

        decidir(conta.id(), enviado.profissional(), "aprovar", TODOS_OS_ITENS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:verificacao-propria"));
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
    }

    @Test
    @DisplayName("CLIENTE ou PROFISSIONAL não decide: 403; sem token: 401")
    void soAdminDecide() throws Exception {
        Enviado enviado = enviado();

        mockMvc.perform(post(BASE + "/" + enviado.profissional() + "/aprovar")
                        .header("Authorization", bearer(enviado.usuario(), Papel.CLIENTE, Papel.PROFISSIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TODOS_OS_ITENS))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + enviado.profissional() + "/aprovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TODOS_OS_ITENS))
                .andExpect(status().isUnauthorized());
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
    }

    @Test
    @DisplayName("varredura dos logs: o fluxo do admin não deixa CPF, celular nem chave Pix na saída (CORE-11)")
    void varreduraDosLogs(CapturedOutput saida) throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();
        String autorizacao = bearer(admin, Papel.ADMIN);
        UUID documento = jdbc.queryForObject(
                "SELECT id FROM documento_verificacao WHERE profissional_id = ? AND tipo = 'rg_frente'",
                UUID.class,
                enviado.profissional());

        mockMvc.perform(get(BASE + "/" + enviado.profissional()).header("Authorization", autorizacao))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + enviado.profissional() + "/documentos/" + documento + "/url")
                        .header("Authorization", autorizacao))
                .andExpect(status().isOk());
        decidir(admin, enviado.profissional(), "correcao", """
                        {"motivo":"Mande a frente do RG de novo."}""").andExpect(status().isOk());
        sms.aguardarEnvios();

        assertThat(saida.getAll())
                .doesNotContain(enviado.cpf().digitos())
                .doesNotContain(enviado.cpf().formatado())
                .doesNotContain(enviado.celular())
                .doesNotContain("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    @DisplayName("motivo com contato ou quebra de linha não vai por SMS: 422 texto-com-contato / 400")
    void motivoComContato() throws Exception {
        Enviado enviado = enviado();
        UUID admin = admin();

        decidir(admin, enviado.profissional(), "recusar", """
                        {"motivo":"Ligue para 47 99999-0000 para resolver."}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        decidir(admin, enviado.profissional(), "correcao", """
                        {"motivo":"Primeira linha
segunda linha"}""").andExpect(status().isBadRequest());
        assertThat(statusDe(enviado.profissional())).isEqualTo("em_analise");
    }

    @Test
    @DisplayName("tirou o certificado de NR-10 na correção: o reenvio volta para nao_tem e aprovar não pede NR-10")
    void nr10TiradoNaCorrecao() throws Exception {
        Enviado enviado = enviado(contas.criarConfirmada(Papel.CLIENTE), true);
        UUID admin = admin();
        decidir(admin, enviado.profissional(), "correcao", """
                        {"motivo":"O certificado está cortado."}""").andExpect(status().isOk());
        UUID certificado = jdbc.queryForObject(
                "SELECT id FROM documento_verificacao WHERE profissional_id = ? AND tipo = 'certificado_nr10'",
                UUID.class,
                enviado.profissional());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                CadastroDeTeste.BASE + "/documentos/" + certificado)
                        .header("Authorization", bearer(enviado.usuario(), Papel.CLIENTE)))
                .andExpect(status().isNoContent());
        enviar(enviado.usuario()).andExpect(status().isOk());

        assertThat(nr10De(enviado.profissional())).isEqualTo("nao_tem");
        decidir(admin, enviado.profissional(), "aprovar", TODOS_OS_ITENS).andExpect(status().isOk());
    }
}
