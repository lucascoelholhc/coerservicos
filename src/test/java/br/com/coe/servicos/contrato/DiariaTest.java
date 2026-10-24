package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * DOM-08 (dia 30): no dia, o profissional marca "Cheguei" (RN35); manda ao menos uma foto e marca "Terminei o dia"
 * (RN36), e a diária fica aguardando o cliente, com a liberação automática marcada para 12 h depois (RN38, PA08).
 * Toda troca segue o diagrama (RN40) e grava o histórico.
 */
class DiariaTest extends ExecucaoDeDiariaTeste {

    private UUID diaria;

    @BeforeEach
    void preparar() {
        diaria = diarias.get(0);
    }

    private String statusDaDiaria() {
        return statusDa(diaria);
    }

    @Test
    @DisplayName("Cheguei no dia: diária em andamento, horário gravado e histórico paga > andamento")
    void chegueiNoDia() throws Exception {
        pagar();
        noDia(dia);

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
        noDia(dia);
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

        cheguei(doCliente(), diaria).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/diarias/" + diaria + "/cheguei")).andExpect(status().isUnauthorized());
        assertThat(statusDaDiaria()).isEqualTo("paga");
    }

    @Test
    @DisplayName("Terminei o dia com foto e recado: aguardando, liberação automática em 12 h, foto guardada")
    void termineiComFoto() throws Exception {
        pagar();
        noDia(dia);
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
        noDia(dia);
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
