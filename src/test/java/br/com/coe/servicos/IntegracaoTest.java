package br.com.coe.servicos;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Base de todo teste de integração: aplicação inteira no perfil {@code test}, Postgres real
 * (Testcontainers), {@link MockMvc} e o relógio controlável {@link RelogioAjustavel}, que volta
 * ao instante padrão antes de cada teste. Todas as subclasses compartilham o mesmo contexto Spring
 * e o mesmo container; não acrescente anotações que mudem o contexto sem necessidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RelogioDeTesteConfiguration.class})
public abstract class IntegracaoTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RelogioAjustavel relogio;

    @Autowired
    private ConfiguracaoNegocio configuracaoNegocio;

    @BeforeEach
    void relogioEConfiguracaoNoEstadoPadrao() {
        relogio.voltarAoPadrao();
        configuracaoNegocio.invalidarCache();
    }
}
