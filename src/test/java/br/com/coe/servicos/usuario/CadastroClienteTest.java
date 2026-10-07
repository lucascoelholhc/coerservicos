package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.IntegracaoTest;

/**
 * POST /api/contas/cliente (CORE-02; RF03, RN58, RNF01, RNF04, RNF15, RNF18). Grava de verdade no
 * container (sem rollback): o usuario não pode ser apagado, então cada teste usa celular e e-mail
 * fictícios únicos.
 */
@ExtendWith(OutputCaptureExtension.class)
class CadastroClienteTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(100);
    private static final String SENHA = "Casa-Azul-2026";

    @Autowired
    JdbcTemplate jdbc;

    private record Contato(String celular, String email) {}

    private static Contato novoContato() {
        long numero = SEQUENCIA.incrementAndGet();
        return new Contato(String.valueOf(47_900_000_000L + numero), "cliente" + numero + "@teste.coe.local");
    }

    private static String corpo(String nome, String celular, String email, String cep, String senha, String versao) {
        return """
                {"nome":"%s","celular":"%s","email":"%s","cep":"%s","senha":"%s","versaoTermosAceita":"%s"}""".formatted(nome, celular, email, cep, senha, versao);
    }

    private ResultActions cadastrar(String json) throws Exception {
        return mockMvc.perform(post("/api/contas/cliente")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Teste/1.0")
                .content(json));
    }

    private ResultActions cadastrar(Contato contato) throws Exception {
        return cadastrar(corpo("Ana Silva", contato.celular(), contato.email(), "89010-000", SENHA, "1.0"));
    }

    @Test
    @DisplayName("201: grava usuário ativo, papel CLIENTE e aceite dos termos; devolve só id e nome")
    void cadastraCliente() throws Exception {
        Contato contato = novoContato();
        String mascarado = "(" + contato.celular().substring(0, 2) + ") "
                + contato.celular().substring(2, 7) + "-" + contato.celular().substring(7);

        String resposta = cadastrar(corpo(
                        "  Ana Silva ",
                        mascarado,
                        "  " + contato.email().toUpperCase() + " ",
                        "89010-000",
                        SENHA,
                        "1.0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Ana Silva"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.celular").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(resposta.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        Map<String, Object> usuario = jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", id);
        assertThat(usuario.get("celular")).isEqualTo(contato.celular());
        assertThat(usuario.get("email").toString()).isEqualTo(contato.email());
        assertThat(usuario.get("cep").toString()).isEqualTo("89010000");
        assertThat(usuario.get("status")).isEqualTo("ativo");
        assertThat(usuario.get("celular_verificado_em")).isNull();
        assertThat(usuario.get("cidade_id")).isNull();
        assertThat(usuario.get("senha_hash").toString()).startsWith("{bcrypt}").isNotEqualTo(SENHA);
        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, id))
                .containsExactly("CLIENTE");
        Map<String, Object> aceite = jdbc.queryForMap(
                "SELECT versao_termos, host(ip) AS ip, user_agent FROM aceite_termos WHERE usuario_id = ?", id);
        assertThat(aceite).containsEntry("versao_termos", "1.0").containsEntry("user_agent", "Teste/1.0");
        assertThat(aceite.get("ip")).isEqualTo("127.0.0.1");
    }

    @ParameterizedTest(name = "400 no campo {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "nome    | A              | 47900099901 | invalido1@teste.coe.local | 89010000 | Casa-Azul-2026",
                "celular | Ana Silva      | 4790009990  | invalido2@teste.coe.local | 89010000 | Casa-Azul-2026",
                "email   | Ana Silva      | 47900099903 | invalido3@               | 89010000 | Casa-Azul-2026",
                "cep     | Ana Silva      | 47900099904 | invalido4@teste.coe.local | 8901000  | Casa-Azul-2026",
                "senha   | Ana Silva      | 47900099905 | invalido5@teste.coe.local | 89010000 | 12345678"
            })
    void campoInvalido(String campo, String nome, String celular, String email, String cep, String senha)
            throws Exception {
        cadastrar(corpo(nome, celular, email, cep, senha, "1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:validacao"))
                .andExpect(jsonPath("$.campos[*].campo").value(campo));
    }

    @Test
    @DisplayName("409: celular não confirmado de outra conta pode ser reivindicado (RN61), dizendo o campo")
    void celularRepetido() throws Exception {
        Contato contato = novoContato();
        cadastrar(contato).andExpect(status().isCreated());

        cadastrar(new Contato(contato.celular(), novoContato().email()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contato-em-uso"))
                .andExpect(jsonPath("$.campo").value("celular"));
    }

    @Test
    @DisplayName("409: e-mail não confirmado de outra conta pode ser reivindicado, mesmo com outra caixa")
    void emailRepetidoOutraCaixa() throws Exception {
        Contato contato = novoContato();
        cadastrar(contato).andExpect(status().isCreated());

        cadastrar(new Contato(novoContato().celular(), contato.email().toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:contato-em-uso"))
                .andExpect(jsonPath("$.campo").value("email"));
    }

    @Test
    @DisplayName("422: versão dos termos diferente da vigente")
    void versaoDosTermosErrada() throws Exception {
        Contato contato = novoContato();

        cadastrar(corpo("Ana Silva", contato.celular(), contato.email(), "89010000", SENHA, "0.9"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:termos-desatualizados"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, contato.celular()))
                .isZero();
    }

    @ParameterizedTest(name = "400: campo extra {0}")
    @CsvSource(
            delimiter = '|',
            value = {"papeis | [\"ADMIN\"]", "status | \"excluido\"", "id | \"00000000-0000-0000-0000-000000000001\""})
    @DisplayName("mass assignment: campo que não existe no pedido é recusado e nada é gravado")
    void massAssignment(String campo, String valor) throws Exception {
        Contato contato = novoContato();
        String json = corpo("Ana Silva", contato.celular(), contato.email(), "89010000", SENHA, "1.0")
                .replace("}", ",\"" + campo + "\":" + valor + "}");

        cadastrar(json).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, contato.celular()))
                .isZero();
    }

    @Test
    @DisplayName("concorrência: dois cadastros simultâneos com o mesmo celular dão um 201 e um 409, nunca 500")
    void concorrenciaMesmoCelular() throws Exception {
        String celular = novoContato().celular();

        assertThat(emParalelo(
                        new Contato(celular, novoContato().email()),
                        new Contato(celular, novoContato().email())))
                .containsExactlyInAnyOrder(201, 409);
    }

    @Test
    @DisplayName("concorrência: dois cadastros simultâneos com o mesmo e-mail dão um 201 e um 409, nunca 500")
    void concorrenciaMesmoEmail() throws Exception {
        String email = novoContato().email();

        assertThat(emParalelo(
                        new Contato(novoContato().celular(), email),
                        new Contato(novoContato().celular(), email)))
                .containsExactlyInAnyOrder(201, 409);
    }

    private List<Integer> emParalelo(Contato primeiro, Contato segundo) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (Contato contato : List.of(primeiro, segundo)) {
            tarefas.add(() -> {
                largada.await();
                return cadastrar(contato).andReturn().getResponse().getStatus();
            });
        }
        List<Future<Integer>> resultados = new ArrayList<>();
        for (Callable<Integer> tarefa : tarefas) {
            resultados.add(executor.submit(tarefa));
        }
        largada.countDown();
        List<Integer> status = new ArrayList<>();
        for (Future<Integer> resultado : resultados) {
            status.add(resultado.get());
        }
        executor.shutdown();
        return status;
    }

    @Test
    @DisplayName("celular com +55 e máscara é gravado só com DDD + número")
    void celularComCodigoDoPais() throws Exception {
        Contato contato = novoContato();
        String comPais = "+55 (" + contato.celular().substring(0, 2) + ") "
                + contato.celular().substring(2, 7) + "-" + contato.celular().substring(7);

        cadastrar(corpo("Ana Silva", comPais, contato.email(), "89010000", SENHA, "1.0"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, contato.celular()))
                .isOne();
    }

    @Test
    @DisplayName("JSON quebrado ou vazio: 400 em Problem Details")
    void jsonInvalido() throws Exception {
        cadastrar("{\"nome\": ").andExpect(status().isBadRequest());
        cadastrar("").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("corpo que não é JSON: 415")
    void formatoNaoSuportado() throws Exception {
        mockMvc.perform(post("/api/contas/cliente")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nome=Ana"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("nenhum log tem senha, celular, e-mail ou CEP, nem no caminho de erro")
    void logsSemDadoPessoal(CapturedOutput saida) throws Exception {
        Contato contato = novoContato();
        cadastrar(corpo("Ana Silva", contato.celular(), contato.email(), "88888888", "Pinheiro-Verde-77", "1.0"))
                .andExpect(status().isCreated());
        cadastrar(corpo("Ana Silva", contato.celular(), contato.email(), "88888888", "Pinheiro-Verde-77", "1.0"))
                .andExpect(status().isConflict());
        cadastrar(corpo("A", contato.celular(), contato.email(), "88888888", "Pinheiro-Verde-77", "1.0"))
                .andExpect(status().isBadRequest());

        assertThat(saida.getAll())
                .doesNotContain(contato.celular())
                .doesNotContain(contato.email())
                .doesNotContain("Pinheiro-Verde-77")
                .doesNotContain("88888888");
    }
}
