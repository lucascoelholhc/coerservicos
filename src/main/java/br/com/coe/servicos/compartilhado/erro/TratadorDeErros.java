package br.com.coe.servicos.compartilhado.erro;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Formato único de erro da API: Problem Details (RFC 9457), com título e detalhe em português,
 * o status do corpo igual ao HTTP e sem nenhum detalhe interno (stack trace, nome de classe, SQL).
 * O tipo de cada erro é {@code urn:coe:erro:<codigo>}.
 */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    static final String PREFIXO_TIPO = "urn:coe:erro:";

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);
    private static final String DETALHE_INTERNO = "Algo deu errado do nosso lado. Tente de novo em instantes.";
    private static final String DETALHE_VALIDACAO = "Confira os campos destacados.";

    /** Campo inválido na validação, como sai em {@code campos} no corpo do erro. */
    public record CampoInvalido(String campo, String mensagem) {}

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail regraDeNegocio(RegraDeNegocioException erro) {
        LOG.info("Regra de negócio impediu a operação: {}", erro.getCodigo());
        return problema(
                HttpStatus.UNPROCESSABLE_CONTENT, erro.getCodigo(), "Não foi possível concluir", erro.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", erro.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException erro) {
        LOG.info("Conflito ao atender a requisição: {}", erro.getCodigo());
        ProblemDetail problema = problema(HttpStatus.CONFLICT, erro.getCodigo(), "Conflito", erro.getMessage());
        if (erro.getCampo() != null) {
            problema.setProperty("campo", erro.getCampo());
        }
        return problema;
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    ResponseEntity<ProblemDetail> naoAutenticado(NaoAutenticadoException erro) {
        ResponseEntity.BodyBuilder resposta =
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        erro.getCabecalhos().forEach((nome, valor) -> resposta.header(nome, valor));
        return resposta.body(problema(HttpStatus.UNAUTHORIZED, erro.getCodigo(), "Não autenticado", erro.getMessage()));
    }

    @ExceptionHandler(AcaoProibidaException.class)
    ProblemDetail acaoProibida(AcaoProibidaException erro) {
        ProblemDetail problema = problema(HttpStatus.FORBIDDEN, erro.getCodigo(), "Acesso negado", erro.getMessage());
        erro.getPropriedades().forEach(problema::setProperty);
        return problema;
    }

    @ExceptionHandler(MuitasTentativasException.class)
    ProblemDetail muitasTentativas(MuitasTentativasException erro) {
        LOG.info("Limite de pedidos atingido");
        return problema(HttpStatus.TOO_MANY_REQUESTS, "muitas-tentativas", "Muitas tentativas", erro.getMessage());
    }

    /** Validação feita fora do controller (ex.: {@code @Validated} num serviço). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail validacaoNoServico(ConstraintViolationException erro) {
        List<CampoInvalido> campos = erro.getConstraintViolations().stream()
                .map(TratadorDeErros::campoInvalido)
                .toList();
        return validacao(campos);
    }

    /**
     * Autenticação e autorização são respondidas pelo Spring Security (401/403), não aqui.
     * Relançar devolve a exceção ao filtro de segurança.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void seguranca(RuntimeException erro) {
        throw erro;
    }

    /**
     * Violação de constraint não tratada pelo serviço: 500 genérico. A mensagem do driver traz os
     * valores da linha ("Key (celular)=(...)"), então o log leva só o tipo, a constraint e o SQLState.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail violacaoNoBanco(DataIntegrityViolationException erro) {
        LOG.error(
                "Erro inesperado ao gravar no banco: {} (constraint {}, SQLState {})",
                erro.getClass().getSimpleName(),
                nomeDaConstraint(erro),
                sqlState(erro));
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "interno", "Erro interno", DETALHE_INTERNO);
    }

    /** Qualquer erro não previsto vira 500 genérico; o detalhe fica só no log. */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception erro) {
        LOG.error("Erro inesperado ao atender a requisição", erro);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "interno", "Erro interno", DETALHE_INTERNO);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erro, HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        List<CampoInvalido> campos = erro.getBindingResult().getFieldErrors().stream()
                .map(TratadorDeErros::campoInvalido)
                .toList();
        return ResponseEntity.badRequest().headers(headers).body(validacao(campos));
    }

    /** Validação de {@code @RequestParam}, {@code @PathVariable} e afins. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException erro, HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        List<CampoInvalido> campos = erro.getParameterValidationResults().stream()
                .flatMap(resultado ->
                        resultado.getResolvableErrors().stream().map(detalhe -> campoInvalido(resultado, detalhe)))
                .toList();
        return ResponseEntity.badRequest().headers(headers).body(validacao(campos));
    }

    /** Demais erros do Spring MVC e {@code ResponseStatusException}: status preservado, texto em português. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erro, @Nullable Object corpo, HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        if (status.is5xxServerError()) {
            LOG.error("Erro do framework ao atender a requisição", erro);
        }
        return super.handleExceptionInternal(erro, traduzir(erro, status), headers, status, requisicao);
    }

    private static ProblemDetail traduzir(Exception erro, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return problema(status, "interno", "Erro interno", DETALHE_INTERNO);
        }
        return switch (status.value()) {
            case 400 ->
                erro instanceof HttpMessageNotReadableException
                        ? problema(
                                status,
                                "requisicao-invalida",
                                "Requisição inválida",
                                "Não foi possível ler os dados enviados.")
                        : problema(status, "requisicao-invalida", "Requisição inválida", "Confira os dados enviados.");
            case 401 -> problema(status, "nao-autenticado", "Não autenticado", "Entre na sua conta para continuar.");
            case 403 -> problema(status, "proibido", "Acesso negado", "Você não tem permissão para fazer isso.");
            case 404 -> problema(status, "nao-encontrado", "Não encontrado", "Endereço não encontrado.");
            case 405 ->
                problema(
                        status,
                        "metodo-nao-permitido",
                        "Método não permitido",
                        "Esta operação não é aceita neste endereço.");
            case 406 ->
                problema(
                        status,
                        "formato-nao-aceito",
                        "Formato não aceito",
                        "Não conseguimos responder no formato pedido.");
            case 409 -> problema(status, "conflito", "Conflito", "A operação conflita com o estado atual.");
            case 413 ->
                problema(
                        status,
                        "conteudo-muito-grande",
                        "Conteúdo muito grande",
                        "O que foi enviado passa do tamanho permitido.");
            case 415 -> problema(status, "formato-nao-suportado", "Formato não suportado", "Envie os dados em JSON.");
            case 429 -> problema(status, "muitas-tentativas", "Muitas tentativas", "Aguarde um pouco e tente de novo.");
            default ->
                problema(
                        status,
                        "requisicao-invalida",
                        "Requisição inválida",
                        "Não foi possível atender a esta requisição.");
        };
    }

    private static String nomeDaConstraint(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violacao) {
                return violacao.getConstraintName();
            }
        }
        return "desconhecida";
    }

    private static String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return "desconhecido";
    }

    private static ProblemDetail validacao(List<CampoInvalido> campos) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "validacao", "Dados inválidos", DETALHE_VALIDACAO);
        problema.setProperty("campos", campos);
        return problema;
    }

    private static CampoInvalido campoInvalido(FieldError erro) {
        return new CampoInvalido(erro.getField(), mensagem(erro.getDefaultMessage()));
    }

    private static CampoInvalido campoInvalido(ParameterValidationResult resultado, MessageSourceResolvable detalhe) {
        String campo = Objects.requireNonNullElse(resultado.getMethodParameter().getParameterName(), "parametro");
        return new CampoInvalido(campo, mensagem(detalhe.getDefaultMessage()));
    }

    private static CampoInvalido campoInvalido(ConstraintViolation<?> violacao) {
        String campo = null;
        for (Path.Node no : violacao.getPropertyPath()) {
            campo = no.getName();
        }
        return new CampoInvalido(Objects.requireNonNullElse(campo, "valor"), mensagem(violacao.getMessage()));
    }

    private static String mensagem(@Nullable String mensagem) {
        return Objects.requireNonNullElse(mensagem, "valor inválido");
    }

    private static ProblemDetail problema(HttpStatusCode status, String codigo, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create(PREFIXO_TIPO + codigo));
        problema.setTitle(titulo);
        return problema;
    }
}
