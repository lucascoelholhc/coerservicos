package br.com.coe.servicos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de todo teste de integração: aplicação inteira no perfil {@code test}, Postgres real
 * (Testcontainers) e {@link MockMvc}. Todas as subclasses compartilham o mesmo contexto Spring
 * e o mesmo container; não acrescente anotações que mudem o contexto sem necessidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegracaoTest {

    @Autowired
    protected MockMvc mockMvc;
}
