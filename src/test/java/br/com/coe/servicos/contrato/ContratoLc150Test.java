package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.profissional.ProfissionaisDeTeste;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/**
 * DOM-06 (dia 26), RN51: mesma diarista + mesmo cliente = no máximo o limite da configuração (2) em qualquer janela
 * de 7 dias seguidos, somando os contratos; cancelada não conta; outra profissão não tem limite.
 */
class ContratoLc150Test extends IntegracaoTest {

    private static final int ITAJAI = 4208203;

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

    private ContasDeTeste contas;
    private ProfissionaisDeTeste.Criado diarista;
    private UUID cliente;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
        diarista = ProfissionaisDeTeste.diarista(contexto);
        cliente = contas.criarConfirmada(Papel.CLIENTE).id();
        assertThat(configuracao.limiteDiaristaJanela7Dias()).isEqualTo(2);
    }

    private LocalDate amanha() {
        return FusoDeNegocio.hoje(clock).plusDays(1);
    }

    private LocalDate proximo(DayOfWeek diaDaSemana) {
        LocalDate dia = amanha();
        while (dia.getDayOfWeek() != diaDaSemana) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private ResultActions contratar(UUID quem, UUID perfil, String servico, LocalDate... dias) throws Exception {
        String lista = Arrays.stream(dias)
                .map(d -> "{\"data\":\"" + d + "\",\"tipo\":\"inteira\"}")
                .collect(Collectors.joining(","));
        String corpo = """
                {"profissionalId":"%s","dias":[%s],"servicos":["%s"],"material":"cliente_tem",
                 "endereco":{"cep":"88301000","cidade":%d,"logradouro":"Rua das Palmeiras","numero":"120"},
                 "versaoTermosAceita":"%s"}""".formatted(perfil, lista, servico, ITAJAI, configuracao.versaoTermos());
        return mockMvc.perform(post("/api/contratos")
                .header("Authorization", bearer(quem, Papel.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions contratarDiarista(UUID quem, LocalDate... dias) throws Exception {
        return contratar(quem, diarista.profissional(), "Limpeza completa", dias);
    }

    private int diariasDaDiarista() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM diaria WHERE cliente_id = ? AND profissional_id = ?",
                Integer.class,
                cliente,
                diarista.profissional());
    }

    @Test
    @DisplayName("duas diárias passam; a terceira em 7 dias, noutro contrato, é recusada com o dia que estoura")
    void terceiraRecusada() throws Exception {
        LocalDate d = amanha();
        contratarDiarista(cliente, d, d.plusDays(1)).andExpect(status().isCreated());

        contratarDiarista(cliente, d.plusDays(3))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:lc150-limite"))
                .andExpect(jsonPath("$.detail").value(containsString("até 2 dias em 7 dias seguidos")));

        assertThat(diariasDaDiarista()).isEqualTo(2);
    }

    @Test
    @DisplayName("três dias da mesma diarista num pedido só: recusado inteiro, nada é gravado")
    void tresNoMesmoPedido() throws Exception {
        LocalDate d = amanha();
        contratarDiarista(cliente, d, d.plusDays(2), d.plusDays(4))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:lc150-limite"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrato WHERE cliente_id = ?", Integer.class, cliente))
                .isZero();
    }

    @Test
    @DisplayName("janela móvel, virando a semana: sexta e sábado + segunda seguinte = recusada; 7 dias depois passa")
    void virandoASemana() throws Exception {
        LocalDate sexta = proximo(DayOfWeek.FRIDAY);
        contratarDiarista(cliente, sexta, sexta.plusDays(1)).andExpect(status().isCreated());

        contratarDiarista(cliente, sexta.plusDays(3))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:lc150-limite"));
        contratarDiarista(cliente, sexta.plusDays(7)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("outro cliente com a mesma diarista não soma; diária cancelada não conta")
    void outroClienteECancelada() throws Exception {
        LocalDate d = amanha();
        String primeiro = contratarDiarista(cliente, d, d.plusDays(1))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        UUID outro = contas.criarConfirmada(Papel.CLIENTE).id();
        contratarDiarista(outro, d.plusDays(2)).andExpect(status().isCreated());

        jdbc.update(
                "UPDATE diaria SET status = 'cancelada', cancelada_em = now() WHERE contrato_id = ? AND data = ?",
                UUID.fromString(JsonPath.read(primeiro, "$.id")),
                d.plusDays(1));
        contratarDiarista(cliente, d.plusDays(3)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("profissão sem LC 150 (jardineiro): três dias úteis seguidos passam")
    void outraProfissaoSemLimite() throws Exception {
        ProfissionaisDeTeste.Criado jardineiro = ProfissionaisDeTeste.ativo(contexto);
        List<LocalDate> uteis = new ArrayList<>();
        for (LocalDate dia = amanha(); uteis.size() < 3; dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                uteis.add(dia);
            }
        }
        contratar(cliente, jardineiro.profissional(), "Corte de grama", uteis.toArray(LocalDate[]::new))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("dois pedidos ao mesmo tempo com uma diária já contratada: só um passa (trava por cliente e diarista)")
    void pedidosAoMesmoTempo() throws Exception {
        LocalDate d = amanha();
        contratarDiarista(cliente, d).andExpect(status().isCreated());

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<Integer> codigos = new ArrayList<>();
        try {
            List<Future<Integer>> respostas = new ArrayList<>();
            for (LocalDate dia : List.of(d.plusDays(1), d.plusDays(2))) {
                respostas.add(threads.submit(() -> {
                    largada.await();
                    return contratarDiarista(cliente, dia)
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            largada.countDown();
            for (Future<Integer> resposta : respostas) {
                codigos.add(resposta.get());
            }
        } finally {
            threads.shutdownNow();
        }
        assertThat(codigos).containsExactlyInAnyOrder(201, 422);
        assertThat(diariasDaDiarista()).isEqualTo(2);
    }
}
