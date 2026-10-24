package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * DOM-08 (dia 30): no dia, o profissional marca "Cheguei" (RN35); manda ao menos uma foto e marca "Terminei o dia"
 * (RN36), e a diária fica aguardando o cliente, com a liberação automática marcada para 12 h depois (RN38, PA08).
 * Toda troca segue o diagrama (RN40) e grava o histórico.
 */
class DiariaTest extends IntegracaoTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    @Autowired
    private Clock clock;

    @Value("${coe.gateway.token-webhook}")
    private String token;

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado profissional;
    private UUID cliente;
    private UUID contrato;
    private UUID diaria;
    private LocalDate dia;

    @BeforeEach
    void preparar() throws Exception {
        contas = new ContasDeTeste(jdbc, codificador);
        profissional = ProfissionaisDeTeste.ativo(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        dia = FusoDeNegocio.hoje(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.THURSDAY) {
            dia = dia.plusDays(1);
        }
        contrato = contratar(dia);
        diaria = jdbc.queryForObject("SELECT id FROM diaria WHERE contrato_id = ?", UUID.class, contrato);
    }

    private UUID contratar(LocalDate data) throws Exception {
        String corpo = """
                {"profissionalId":"%s","dias":[{"data":"%s","tipo":"inteira"}],"servicos":["Corte de grama"],
                 "material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":4208203,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(profissional.profissional(), data, configuracao.versaoTermos());
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
    private void pagar() throws Exception {
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
        BigDecimal total =
                jdbc.queryForObject("SELECT valor_total FROM contrato WHERE id = ?", BigDecimal.class, contrato);
        mockMvc.perform(post("/api/webhooks/pagamento")
                        .header("asaas-access-token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"id\":\"evt_diaria_%s\",\"event\":\"PAYMENT_RECEIVED\",\"payment\":{\"id\":\"%s\",\"value\":%s}}"
                                        .formatted(UUID.randomUUID(), externo, total.toPlainString())))
                .andExpect(status().isOk());
    }

    /** O relógio no dia da diária, às 8 h de São Paulo. */
    private void noDia() {
        relogio.fixarEm(
                dia.atTime(LocalTime.of(8, 0)).atZone(FusoDeNegocio.ZONA).toInstant());
    }

    private String doProfissional() {
        return bearer(profissional.usuario(), Papel.PROFISSIONAL);
    }

    private ResultActions cheguei(String autorizacao, UUID id) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + id + "/cheguei").header("Authorization", autorizacao));
    }

    private ResultActions foto(String autorizacao, UUID id, byte[] arquivo) throws Exception {
        return mockMvc.perform(multipart("/api/diarias/fotos")
                .file(new MockMultipartFile("arquivo", "servico.jpg", "image/jpeg", arquivo))
                .param("diaria", id.toString())
                .with(comTamanho(arquivo.length + 512))
                .header("Authorization", autorizacao));
    }

    private ResultActions terminei(String autorizacao, UUID id, String corpo) throws Exception {
        return mockMvc.perform(post("/api/diarias/" + id + "/terminei")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String statusDaDiaria() {
        return jdbc.queryForObject("SELECT status FROM diaria WHERE id = ?", String.class, diaria);
    }

    /** MockMvc não põe Content-Length no multipart; o filtro de corpo exige nas rotas de upload. */
    private static RequestPostProcessor comTamanho(int tamanho) {
        return requisicao -> {
            requisicao.setContent(new byte[tamanho]);
            return requisicao;
        };
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage imagem = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", saida);
        return saida.toByteArray();
    }

    @Test
    @DisplayName("Cheguei no dia: diária em andamento, horário gravado e histórico paga > andamento")
    void chegueiNoDia() throws Exception {
        pagar();
        noDia();

        cheguei(doProfissional(), diaria)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("andamento"))
                .andExpect(jsonPath("$.chegouEm").isNotEmpty());

        assertThat(statusDaDiaria()).isEqualTo("andamento");
        assertThat(jdbc.queryForList(
                        "SELECT de_status || '>' || para_status || '/' || origem FROM historico_diaria"
                                + " WHERE diaria_id = ? ORDER BY id",
                        String.class,
                        diaria))
                .endsWith("paga>andamento/profissional");
        // de novo: andamento > andamento não existe no diagrama
        cheguei(doProfissional(), diaria)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));
    }

    @Test
    @DisplayName("Cheguei antes do dia: 422 fora-do-dia; diária não paga: 422 transicao-invalida")
    void chegueiForaDeHora() throws Exception {
        cheguei(doProfissional(), diaria)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));

        pagar();
        cheguei(doProfissional(), diaria)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:fora-do-dia"));
        assertThat(statusDaDiaria()).isEqualTo("paga");
    }

    @Test
    @DisplayName("diária de outro profissional = 404 igual a inexistente; cliente = 403; sem token = 401")
    void antiIdor() throws Exception {
        pagar();
        noDia();
        ProfissionaisDeTeste.Criado outro = ProfissionaisDeTeste.ativo(contexto);
        String autorizacao = bearer(outro.usuario(), Papel.PROFISSIONAL);

        String alheia = cheguei(autorizacao, diaria)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String inexistente = cheguei(autorizacao, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(semRequisicao(alheia).replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(semRequisicao(inexistente).replaceAll("\"instance\":\"[^\"]*\"", ""));
        foto(autorizacao, diaria, jpeg()).andExpect(status().isNotFound());
        terminei(autorizacao, diaria, "{}").andExpect(status().isNotFound());

        cheguei(bearer(cliente, Papel.CLIENTE), diaria).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/diarias/" + diaria + "/cheguei")).andExpect(status().isUnauthorized());
        assertThat(statusDaDiaria()).isEqualTo("paga");
    }

    @Test
    @DisplayName("Terminei o dia com foto e recado: aguardando, liberação automática em 12 h, foto guardada")
    void termineiComFoto() throws Exception {
        pagar();
        noDia();
        cheguei(doProfissional(), diaria).andExpect(status().isOk());

        foto(doProfissional(), diaria, jpeg()).andExpect(status().isCreated());
        relogio.avancar(Duration.ofHours(8));
        terminei(doProfissional(), diaria, "{\"recado\":\"Grama cortada e calçada varrida.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aguardando"))
                .andExpect(jsonPath("$.autoLiberaEm").isNotEmpty());

        Map<String, Object> linha =
                jdbc.queryForMap("SELECT terminou_em, auto_libera_em FROM diaria WHERE id = ?", diaria);
        Duration prazo = Duration.between(
                ((Timestamp) linha.get("terminou_em")).toInstant(),
                ((Timestamp) linha.get("auto_libera_em")).toInstant());
        assertThat(prazo).isEqualTo(configuracao.autoLiberacao());
        assertThat(jdbc.queryForList("SELECT tipo FROM evidencia_diaria WHERE diaria_id = ?", String.class, diaria))
                .containsExactly("fim_do_dia");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM evidencia_diaria WHERE diaria_id = ? AND observacao IS NOT NULL",
                        Integer.class,
                        diaria))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT de_status || '>' || para_status FROM historico_diaria WHERE diaria_id = ? ORDER BY id",
                        String.class,
                        diaria))
                .endsWith("andamento>aguardando");
    }

    @Test
    @DisplayName("Terminei sem foto, com contato no recado ou antes do Cheguei: recusado")
    void termineiRecusado() throws Exception {
        pagar();
        noDia();
        terminei(doProfissional(), diaria, "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));
        foto(doProfissional(), diaria, jpeg())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:transicao-invalida"));

        cheguei(doProfissional(), diaria).andExpect(status().isOk());
        terminei(doProfissional(), diaria, "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:foto-obrigatoria"));
        foto(doProfissional(), diaria, "não é imagem".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        foto(doProfissional(), diaria, jpeg()).andExpect(status().isCreated());
        terminei(doProfissional(), diaria, "{\"recado\":\"me liga no 47 99123-4567\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:texto-com-contato"));
        assertThat(statusDaDiaria()).isEqualTo("andamento");
    }
}
