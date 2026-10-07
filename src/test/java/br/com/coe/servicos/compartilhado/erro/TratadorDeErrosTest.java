package br.com.coe.servicos.compartilhado.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.coe.servicos.IntegracaoTest;

/**
 * Todo erro da API sai em Problem Details (RFC 9457), com textos em português, o status do corpo
 * igual ao HTTP e sem detalhe interno (stack trace, nome de classe, SQL).
 */
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
@Import(TratadorDeErrosTest.ControladorDeErros.class)
class TratadorDeErrosTest extends IntegracaoTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Endpoints que só existem neste teste. Por ser aninhado num teste, o Spring Boot não o pega
     * no scan dos outros contextos; ele entra só aqui, pelo {@code @Import}.
     */
    @RestController
    @RequestMapping("/teste/erros")
    static class ControladorDeErros {

        record PedidoDeTeste(
                @NotBlank String nome, @Size(min = 15) String descricao) {}

        record ItemDeTeste(@Min(1) int quantidade) {}

        record PrecoDeTeste(br.com.coe.servicos.compartilhado.dinheiro.Dinheiro valor) {}

        @PostMapping("/preco")
        PrecoDeTeste preco(@RequestBody PrecoDeTeste preco) {
            return preco;
        }

        @PostMapping("/validacao")
        PedidoDeTeste validacao(@Valid @RequestBody PedidoDeTeste pedido) {
            return pedido;
        }

        @GetMapping("/parametro")
        int parametro(@RequestParam @Min(1) int quantidade) {
            return quantidade;
        }

        @GetMapping("/caminho/{numero}")
        int caminho(@PathVariable int numero) {
            return numero;
        }

        @GetMapping("/servico")
        void validacaoNoServico() {
            Set<? extends jakarta.validation.ConstraintViolation<?>> violacoes =
                    Validation.buildDefaultValidatorFactory().getValidator().validate(new ItemDeTeste(0));
            throw new ConstraintViolationException(violacoes);
        }

        @GetMapping("/nao-encontrado")
        void naoEncontrado() {
            throw new RecursoNaoEncontradoException("Profissional não encontrado.");
        }

        @GetMapping("/conflito")
        void conflito() {
            throw new ConflitoException("Este dia já está reservado.");
        }

        @GetMapping("/regra")
        void regra() {
            throw new RegraDeNegocioException("limite-lc150", "A diarista já tem 2 diárias com você nestes 7 dias.");
        }

        @GetMapping("/status/{codigo}")
        void status(@PathVariable int codigo) {
            throw new ResponseStatusException(HttpStatus.valueOf(codigo), "detalhe interno do framework");
        }

        @GetMapping("/proibido")
        void proibido() {
            throw new AccessDeniedException("sem permissão");
        }

        @GetMapping("/integridade")
        void integridade() {
            throw new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint \"uq_aceite_termos\""
                            + " Detail: Key (celular)=(47900000999) already exists.");
        }

        @GetMapping("/concorrencia")
        void concorrencia() {
            throw new org.springframework.dao.OptimisticLockingFailureException("versão 3 mudou para 4");
        }

        @GetMapping("/inesperado")
        void inesperado() {
            throw new IllegalStateException("detalhe interno: SELECT * FROM usuario");
        }
    }

    @Test
    @DisplayName("400: validação do corpo lista cada campo com a mensagem em português")
    void validacaoDoCorpo() throws Exception {
        mockMvc.perform(post("/teste/erros/validacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": " ", "descricao": "curta"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:validacao"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value("Confira os campos destacados."))
                .andExpect(jsonPath("$.campos[*].campo").value(containsInAnyOrder("nome", "descricao")))
                .andExpect(jsonPath("$.campos[?(@.campo == 'nome')].mensagem").value("não deve estar em branco"));
    }

    @Test
    @DisplayName("400: mensagens continuam em português mesmo se o navegador pedir inglês")
    void localeFixo() throws Exception {
        mockMvc.perform(post("/teste/erros/validacao")
                        .with(csrf())
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": " ", "descricao": "uma descrição bem longa"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].mensagem").value("não deve estar em branco"));
    }

    @Test
    @DisplayName("400: validação de parâmetro também lista os campos")
    void validacaoDeParametro() throws Exception {
        mockMvc.perform(get("/teste/erros/parametro").param("quantidade", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:validacao"))
                .andExpect(jsonPath("$.campos[0].campo").value("quantidade"))
                .andExpect(jsonPath("$.campos[0].mensagem").value("deve ser maior que ou igual à 1"));
    }

    @Test
    @DisplayName("400: validação feita no serviço (ConstraintViolationException) vira dados inválidos")
    void validacaoNoServico() throws Exception {
        mockMvc.perform(get("/teste/erros/servico"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:validacao"))
                .andExpect(jsonPath("$.campos[0].campo").value("quantidade"));
    }

    @Test
    @DisplayName("400: parâmetro do tipo errado ou ausente")
    void parametroInvalido() throws Exception {
        mockMvc.perform(get("/teste/erros/caminho/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:requisicao-invalida"))
                .andExpect(jsonPath("$.detail").value("Confira os dados enviados."));
        mockMvc.perform(get("/teste/erros/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confira os dados enviados."));
    }

    @Test
    @DisplayName("400: valor em dinheiro com 3 casas ou negativo no corpo (nunca 500)")
    void dinheiroInvalidoNoCorpo() throws Exception {
        for (String valor : new String[] {"280.005", "-1.00", "abc"}) {
            mockMvc.perform(post("/teste/erros/preco")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\":\"" + valor + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:coe:erro:requisicao-invalida"));
        }
    }

    @Test
    @DisplayName("400: corpo que não é JSON válido")
    void corpoInvalido() throws Exception {
        mockMvc.perform(post("/teste/erros/validacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ isto não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:requisicao-invalida"))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Não foi possível ler os dados enviados."));
    }

    @Test
    @DisplayName("403: AccessDeniedException segue para o Spring Security (não vira 500)")
    void acessoNegado() throws Exception {
        mockMvc.perform(get("/teste/erros/proibido")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("403 e 429 lançados com ResponseStatusException mantêm o status e ganham tipo próprio")
    void responseStatusException() throws Exception {
        mockMvc.perform(get("/teste/erros/status/403"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:proibido"))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
        mockMvc.perform(get("/teste/erros/status/429"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:muitas-tentativas"))
                .andExpect(jsonPath("$.title").value("Muitas tentativas"));
    }

    @Test
    @DisplayName("503: corpo traz o mesmo status do HTTP e nenhum detalhe interno")
    void servicoIndisponivel() throws Exception {
        mockMvc.perform(get("/teste/erros/status/503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:interno"))
                .andExpect(jsonPath("$.detail").value("Algo deu errado do nosso lado. Tente de novo em instantes."));
    }

    @Test
    @DisplayName("404: recurso não encontrado")
    void naoEncontrado() throws Exception {
        mockMvc.perform(get("/teste/erros/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:nao-encontrado"))
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.detail").value("Profissional não encontrado."));
    }

    @Test
    @DisplayName("404: endereço que não existe na API")
    void enderecoInexistente() throws Exception {
        mockMvc.perform(get("/teste/erros/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.detail").value("Endereço não encontrado."));
    }

    @Test
    @DisplayName("405: método HTTP não aceito no endereço")
    void metodoNaoAceito() throws Exception {
        mockMvc.perform(post("/teste/erros/conflito").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    @DisplayName("409: conflito com o estado atual")
    void conflito() throws Exception {
        mockMvc.perform(get("/teste/erros/conflito"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:conflito"))
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail").value("Este dia já está reservado."));
    }

    @Test
    @DisplayName("422: regra de negócio, com o código da regra no tipo")
    void regraDeNegocio() throws Exception {
        mockMvc.perform(get("/teste/erros/regra"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:limite-lc150"))
                .andExpect(jsonPath("$.title").value("Não foi possível concluir"))
                .andExpect(jsonPath("$.detail").value("A diarista já tem 2 diárias com você nestes 7 dias."));
    }

    @Test
    @DisplayName("409: a linha mudou no meio do caminho (trava otimista), sem detalhe interno")
    void edicaoConcorrente() throws Exception {
        mockMvc.perform(get("/teste/erros/concorrencia"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:edicao-concorrente"))
                .andExpect(jsonPath("$.detail").value("Seus dados mudaram enquanto você salvava. Tente de novo."));
    }

    @Test
    @DisplayName("500: erro inesperado sem nenhum detalhe interno")
    void erroInesperado() throws Exception {
        mockMvc.perform(get("/teste/erros/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.type").value("urn:coe:erro:interno"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Algo deu errado do nosso lado. Tente de novo em instantes."))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("500 por violação no banco: o log não leva os valores da linha (celular, e-mail)")
    void violacaoNoBancoSemDadoPessoalNoLog(CapturedOutput saida) throws Exception {
        mockMvc.perform(get("/teste/erros/integridade"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:interno"));

        assertThat(saida.getAll()).doesNotContain("47900000999").contains("DataIntegrityViolationException");
    }
}
