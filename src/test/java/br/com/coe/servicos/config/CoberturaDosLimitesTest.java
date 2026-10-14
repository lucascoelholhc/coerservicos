package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/**
 * CORE-07: negar por padrão também no rate limit. Toda rota da API que muda algo (POST, PUT, PATCH,
 * DELETE) tem limite por IP ou global ({@link RotasLimitadas}), limite por usuário
 * ({@link LimitadoPorUsuario}) ou está nas exceções abaixo, com o motivo. Endpoint novo sem nada disso
 * quebra o build. O teto geral de /api/** por IP vale para todas, inclusive as exceções.
 */
class CoberturaDosLimitesTest extends IntegracaoTest {

    /** Rotas sem limite próprio, com o motivo (só o teto geral por IP). */
    private static final Map<String, String> EXCECOES = Map.of(
            "POST /api/auth/sair", "só encerra a sessão de quem chama; sem envio, sem senha",
            "POST /api/auth/sair-de-todos", "só encerra as sessões de quem chama; sem envio, sem senha");

    private static final Set<RequestMethod> MUDAM_ALGO =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    @Test
    @DisplayName("toda rota que muda algo em /api tem limite próprio ou está nas exceções com motivo")
    void todaRotaTemLimite() {
        Set<String> porIp = RotasLimitadas.ROTAS.stream()
                .map(rota -> rota.metodo().name() + " " + rota.caminho())
                .collect(Collectors.toSet());

        mapeamentos
                .getHandlerMethods()
                .forEach((info, metodo) -> rotasQueMudam(info)
                        .forEach(rota -> assertThat(porIp.contains(rota)
                                        || metodo.hasMethodAnnotation(LimitadoPorUsuario.class)
                                        || EXCECOES.containsKey(rota))
                                .as(
                                        "%s sem limite: inclua em RotasLimitadas, anote com @LimitadoPorUsuario"
                                                + " ou justifique nas exceções",
                                        rota)
                                .isTrue()));
    }

    @Test
    @DisplayName("as exceções existem (lista sem rota morta)")
    void excecoesExistem() {
        Set<String> rotas = mapeamentos.getHandlerMethods().keySet().stream()
                .flatMap(CoberturaDosLimitesTest::rotasQueMudam)
                .collect(Collectors.toSet());

        assertThat(rotas).containsAll(EXCECOES.keySet());
    }

    @Test
    @DisplayName("a regra da anotação existe na configuração (sem nome digitado errado)")
    void regrasDasAnotacoesExistem(
            @Autowired br.com.coe.servicos.compartilhado.limite.PropriedadesDeLimites propriedades) {
        mapeamentos.getHandlerMethods().values().stream()
                .map((HandlerMethod metodo) -> metodo.getMethodAnnotation(LimitadoPorUsuario.class))
                .filter(java.util.Objects::nonNull)
                .forEach(anotacao -> assertThat(propriedades.regras()).containsKey(anotacao.value()));
    }

    private static java.util.stream.Stream<String> rotasQueMudam(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .filter(MUDAM_ALGO::contains)
                .flatMap(metodo -> info.getPatternValues().stream()
                        .filter(caminho -> caminho.startsWith("/api/"))
                        .map(caminho -> metodo.name() + " " + caminho));
    }
}
