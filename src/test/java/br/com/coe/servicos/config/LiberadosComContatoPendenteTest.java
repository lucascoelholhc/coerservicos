package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.seguranca.LiberadoComContatoPendente;

/**
 * Congela a lista de liberados com contato pendente (RN61): endpoint novo com a anotação muda
 * esta lista de propósito, com revisão. No DOM-08 entram Cheguei, Terminei o dia e aprovar diária.
 */
class LiberadosComContatoPendenteTest extends IntegracaoTest {

    private static final Set<String> LIBERADOS = Set.of(
            "POST /api/auth/entrar",
            "POST /api/auth/segundo-passo",
            "POST /api/auth/codigo",
            "POST /api/auth/entrar-com-codigo",
            "POST /api/auth/renovar",
            "POST /api/auth/sair",
            "POST /api/auth/sair-de-todos",
            "GET /api/contas/eu",
            "PUT /api/contas/eu/celular",
            "POST /api/contas/eu/celular/codigo",
            "POST /api/contas/eu/celular/confirmar",
            "PUT /api/contas/eu/email",
            "POST /api/contas/eu/email/confirmacao");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamento;

    @Test
    @DisplayName("os endpoints liberados com contato pendente são exatamente estes")
    void listaCongelada() {
        Set<String> anotados = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada :
                mapeamento.getHandlerMethods().entrySet()) {
            HandlerMethod metodo = entrada.getValue();
            boolean liberado = metodo.hasMethodAnnotation(LiberadoComContatoPendente.class)
                    || metodo.getBeanType().isAnnotationPresent(LiberadoComContatoPendente.class);
            if (!liberado || !metodo.getBeanType().getPackageName().startsWith("br.com.coe.servicos")) {
                continue;
            }
            for (RequestMethod verbo : entrada.getKey().getMethodsCondition().getMethods()) {
                for (String caminho : entrada.getKey().getPatternValues()) {
                    anotados.add(verbo.name() + " " + caminho);
                }
            }
        }

        assertThat(anotados).containsExactlyInAnyOrderElementsOf(LIBERADOS);
    }
}
