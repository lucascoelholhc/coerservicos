package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.seguranca.Publico;

/**
 * Negar por padrão (CORE-06): as rotas liberadas na SegurancaConfig e os endpoints com @Publico
 * são exatamente os mesmos, nos dois sentidos (o health do actuator fica fora, não é controller).
 * E, na prática, todo endpoint que não é @Publico responde 401 sem token.
 */
class EndpointsPublicosTest extends IntegracaoTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamento;

    record Rota(HttpMethod metodo, String caminho) {}

    private Map<Rota, Boolean> rotasDaCoe() {
        Map<Rota, Boolean> rotas = new java.util.HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada :
                mapeamento.getHandlerMethods().entrySet()) {
            HandlerMethod metodo = entrada.getValue();
            if (!metodo.getBeanType().getPackageName().startsWith("br.com.coe.servicos")) {
                continue;
            }
            boolean publico = metodo.hasMethodAnnotation(Publico.class);
            for (RequestMethod verbo : entrada.getKey().getMethodsCondition().getMethods()) {
                for (String caminho : entrada.getKey().getPatternValues()) {
                    rotas.put(new Rota(HttpMethod.valueOf(verbo.name()), caminho), publico);
                }
            }
        }
        return rotas;
    }

    @Test
    @DisplayName("toda rota @Publico está liberada na SegurancaConfig e toda rota liberada tem @Publico")
    void publicoIgualAoLiberado() {
        Set<Rota> comPublico = new HashSet<>();
        rotasDaCoe().forEach((rota, publico) -> {
            if (publico) {
                comPublico.add(rota);
            }
        });
        Set<Rota> liberadas = new HashSet<>();
        SegurancaConfig.ROTAS_PUBLICAS.forEach(rota -> liberadas.add(new Rota(rota.metodo(), rota.caminho())));

        assertThat(comPublico).isNotEmpty().isEqualTo(liberadas);
    }

    @Test
    @DisplayName("sem token, todo endpoint que não é @Publico responde 401 do filtro de segurança")
    void naoPublicoExigeToken() throws Exception {
        for (Map.Entry<Rota, Boolean> rota : rotasDaCoe().entrySet()) {
            String caminho = rota.getKey()
                    .caminho()
                    .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            String corpo = mockMvc.perform(request(rota.getKey().metodo(), caminho)
                            .header("Origin", ORIGEM_DO_FRONT)
                            .contentType("application/json")
                            .content("{}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            boolean barradoPeloFiltro = corpo.contains("urn:coe:erro:nao-autenticado");
            assertThat(barradoPeloFiltro)
                    .as("%s %s (@Publico = %s)", rota.getKey().metodo(), caminho, rota.getValue())
                    .isEqualTo(!rota.getValue());
        }
    }
}
