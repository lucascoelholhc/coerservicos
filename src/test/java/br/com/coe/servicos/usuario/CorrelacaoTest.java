package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
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
import br.com.coe.servicos.compartilhado.mensageria.EnviadorSmsEmMemoria;

/**
 * CORE-11: id de correlação por requisição. O filtro gera um id novo (o X-Request-Id de fora é
 * ignorado), põe no MDC ("requisicao"), devolve no header X-Request-Id e no Problem Details (campo
 * requisicao) de todo erro, e o id segue para a thread da fila de envio.
 */
@ExtendWith(OutputCaptureExtension.class)
class CorrelacaoTest extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    private MockHttpServletResponse executar(MockHttpServletRequestBuilder pedido) throws Exception {
        return mockMvc.perform(pedido).andReturn().getResponse();
    }

    private static String idValido(MockHttpServletResponse resposta) {
        String id = resposta.getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id)).isNotNull();
        return id;
    }

    @Test
    @DisplayName("toda resposta leva X-Request-Id; o de fora é ignorado; o MDC não vaza para a thread")
    void header() throws Exception {
        MockHttpServletResponse resposta =
                executar(get("/api/publico/regras").header("X-Request-Id", "forjado-pelo-cliente"));

        assertThat(idValido(resposta)).isNotEqualTo("forjado-pelo-cliente");
        assertThat(MDC.get("requisicao")).isNull();
    }

    @Test
    @DisplayName("Problem Details leva o mesmo id em 401, 403, 413, 422 e 429")
    void problemDetails() throws Exception {
        List<MockHttpServletRequestBuilder> erros = List.of(
                get("/api/contas/eu"), // 401 do Security
                post("/api/auth/renovar"), // 403 do FiltroDeOrigem (sem Origin)
                post("/api/contas/cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "x".repeat(70 * 1024) + "\"}"), // 413
                post("/api/contas/email/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalido\"}")); // 422 do TratadorDeErros
        for (MockHttpServletRequestBuilder pedido : erros) {
            MockHttpServletResponse resposta = executar(pedido);
            String id = idValido(resposta);

            assertThat((String) JsonPath.read(resposta.getContentAsString(), "$.requisicao"))
                    .as("%s", resposta.getStatus())
                    .isEqualTo(id);
        }
        MockHttpServletResponse limite = null;
        for (int i = 0; i < 11; i++) {
            limite = executar(post("/api/auth/codigo")
                    .with(requisicao -> {
                        requisicao.setRemoteAddr("203.0.113.90");
                        return requisicao;
                    })
                    .header("Origin", ORIGEM_DO_FRONT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"celular\":\"47900000990\"}"));
        }
        assertThat(limite.getStatus()).isEqualTo(429);
        assertThat((String) JsonPath.read(limite.getContentAsString(), "$.requisicao"))
                .isEqualTo(idValido(limite));
    }

    @Test
    @DisplayName("o id segue para a thread de envio: o SMS e o log do envio mostram o id da requisição")
    void filaDeEnvio(CapturedOutput saida) throws Exception {
        String celular = new ContasDeTeste(jdbc, codificador)
                .criarConfirmada(Papel.CLIENTE)
                .celular();

        MockHttpServletResponse resposta = executar(post("/api/auth/codigo")
                .header("Origin", ORIGEM_DO_FRONT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\":\"" + celular + "\"}"));
        String id = idValido(resposta);
        List<EnviadorSmsEmMemoria.Sms> enviados = sms.para(celular);

        assertThat(enviados).isNotEmpty();
        assertThat(enviados.getLast().requisicao()).isEqualTo(id);
        assertThat(saida.getOut()).containsPattern("\\[" + id + "\\][^\\n]*Enviado SMS para \\d{2}\\*+\\d{4}");
    }
}
