package br.com.coe.servicos.contrato;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * Base dos testes da execução da diária (DOM-08): um jardineiro ativo (segunda a sexta), um cliente e um contrato
 * para as quintas-feiras escolhidas, pago pelo webhook do gateway falso quando o teste pede. Cada teste tem os
 * próprios dados (o banco não é limpo entre testes).
 */
abstract class ExecucaoDeDiariaTeste extends IntegracaoTest {

    @Autowired
    protected ApplicationContext contexto;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ConfiguracaoNegocio configuracao;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private Clock clock;

    @Value("${coe.gateway.token-webhook}")
    private String token;

    protected ContasDeTeste contas;
    protected ProfissionaisDeTeste.Criado profissional;
    protected UUID cliente;
    protected UUID contrato;
    /** As diárias do contrato, na ordem das datas. */
    protected List<UUID> diarias;
    /** A primeira quinta-feira depois de hoje (o dia da primeira diária). */
    protected LocalDate dia;

    /** Quantas quintas-feiras seguidas o contrato tem (uma por padrão). */
    protected int quantasDiarias() {
        return 1;
    }

    @BeforeEach
    void prepararContrato() throws Exception {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.THURSDAY) {
            dia = dia.plusDays(1);
        }
        LocalDate[] datas = new LocalDate[quantasDiarias()];
        for (int i = 0; i < datas.length; i++) {
            datas[i] = dia.plusWeeks(i);
        }
        contrato = contratar(datas);
        diarias = jdbc.queryForList("SELECT id FROM diaria WHERE contrato_id = ? ORDER BY data", UUID.class, contrato);
    }

    private UUID contratar(LocalDate... datas) throws Exception {
        String dias = Arrays.stream(datas)
                .map(d -> "{\"data\":\"" + d + "\",\"tipo\":\"inteira\"}")
                .collect(Collectors.joining(","));
        String corpo = """
                {"profissionalId":"%s","dias":[%s],"servicos":["Corte de grama"],"material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":4208203,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(profissional.profissional(), dias, configuracao.versaoTermos());
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/contratos")
                                .header("Authorization", bearer(cliente, Papel.CLIENTE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id"));
    }

    /** Paga o contrato pelo webhook do gateway falso, como o Asaas faria. */
    protected void pagar() throws Exception {
        String cobranca = JsonPath.read(
                mockMvc.perform(post("/api/contratos/" + contrato + "/pagamento")
                                .header("Authorization", bearer(cliente, Papel.CLIENTE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"metodo\":\"pix\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.cobrancaId");
        String externo = jdbc.queryForObject(
                "SELECT id_externo FROM cobranca WHERE id = ?", String.class, UUID.fromString(cobranca));
        mockMvc.perform(post("/api/webhooks/pagamento")
                        .header("asaas-access-token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"id\":\"evt_diaria_%s\",\"event\":\"PAYMENT_RECEIVED\",\"payment\":{\"id\":\"%s\",\"value\":%s}}"
                                        .formatted(
                                                UUID.randomUUID(),
                                                externo,
                                                totalDoContrato().toPlainString())))
                .andExpect(status().isOk());
    }

    protected BigDecimal totalDoContrato() {
        return jdbc.queryForObject("SELECT valor_total FROM contrato WHERE id = ?", BigDecimal.class, contrato);
    }

    /** O relógio no dia da diária, às 8 h de São Paulo. */
    protected void noDia(LocalDate data) {
        relogio.fixarEm(
                data.atTime(LocalTime.of(8, 0)).atZone(FusoDeNegocio.ZONA).toInstant());
    }

    protected String doProfissional() {
        return bearer(profissional.usuario(), Papel.PROFISSIONAL);
    }

    protected String doCliente() {
        return bearer(cliente, Papel.CLIENTE);
    }

    protected ResultActions cheguei(String autorizacao, UUID id) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + id + "/cheguei").header("Authorization", autorizacao));
    }

    protected ResultActions foto(String autorizacao, UUID id, byte[] arquivo) throws Exception {
        return mockMvc.perform(multipart("/api/diarias/fotos")
                .file(new MockMultipartFile("arquivo", "servico.jpg", "image/jpeg", arquivo))
                .param("diaria", id.toString())
                .with(comTamanho(arquivo.length + 512))
                .header("Authorization", autorizacao));
    }

    protected ResultActions terminei(String autorizacao, UUID id, String corpo) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + id + "/terminei")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    protected ResultActions aprovar(String autorizacao, UUID id) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + id + "/aprovar").header("Authorization", autorizacao));
    }

    /** Pago, no dia: Cheguei, uma foto e Terminei às 16 h; a diária fica aguardando o cliente. */
    protected void trabalharODia(UUID id, LocalDate data) throws Exception {
        noDia(data);
        cheguei(doProfissional(), id).andExpect(status().isOk());
        foto(doProfissional(), id, jpeg()).andExpect(status().isCreated());
        relogio.avancar(Duration.ofHours(8));
        terminei(doProfissional(), id, "{}").andExpect(status().isOk());
    }

    protected String statusDa(UUID id) {
        return jdbc.queryForObject("SELECT status FROM diaria WHERE id = ?", String.class, id);
    }

    /** Créditos − débitos da conta (gateway, custodia, receita_coe, profissional) nas transações deste contrato. */
    protected BigDecimal saldoNoContrato(String tipoDaConta) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE l.natureza WHEN 'C' THEN l.valor ELSE -l.valor END), 0)
                  FROM lancamento l
                  JOIN transacao_financeira t ON t.id = l.transacao_id
                  JOIN conta_razao r ON r.id = l.conta_id
                 WHERE t.contrato_id = ? AND r.tipo = ?
                """, BigDecimal.class, contrato, tipoDaConta);
    }

    /** MockMvc não põe Content-Length no multipart; o filtro de corpo exige nas rotas de upload. */
    protected static RequestPostProcessor comTamanho(int tamanho) {
        return requisicao -> {
            requisicao.setContent(new byte[tamanho]);
            return requisicao;
        };
    }

    protected static byte[] jpeg() throws Exception {
        BufferedImage imagem = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", saida);
        return saida.toByteArray();
    }
}
