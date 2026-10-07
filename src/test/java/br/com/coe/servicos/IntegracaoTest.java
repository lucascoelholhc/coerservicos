package br.com.coe.servicos;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.usuario.EmissorDeToken;
import br.com.coe.servicos.usuario.Papel;

/**
 * Base de todo teste de integração: aplicação inteira no perfil {@code test}, Postgres real
 * (Testcontainers), {@link MockMvc} e o relógio controlável {@link RelogioAjustavel}, que volta
 * ao instante padrão antes de cada teste. Todas as subclasses compartilham o mesmo contexto Spring
 * e o mesmo container; não acrescente anotações que mudem o contexto sem necessidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    RelogioDeTesteConfiguration.class,
    br.com.coe.servicos.compartilhado.mensageria.MensageriaDeTesteConfiguration.class
})
public abstract class IntegracaoTest {

    /** Origem do front no perfil test (a mesma do local): obrigatória no renovar e no sair. */
    protected static final String ORIGEM_DO_FRONT = "http://localhost:5173";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RelogioAjustavel relogio;

    /** SMS falso: o teste lê o código enviado com {@code sms.ultimoCodigo(celular)}. */
    @Autowired
    protected br.com.coe.servicos.compartilhado.mensageria.EnviadorSmsEmMemoria sms;

    /** E-mail falso: o teste lê o link com {@code email.ultimoToken(endereco)}. */
    @Autowired
    protected br.com.coe.servicos.compartilhado.mensageria.EnviadorEmailEmMemoria email;

    @Autowired
    private ConfiguracaoNegocio configuracaoNegocio;

    @Autowired
    private EmissorDeToken emissorDeToken;

    /**
     * Header {@code Authorization} com um token de acesso válido para o usuário e os papéis dados,
     * emitido pelo emissor real (sem passar pelo login nem pelo MFA). Use para testar endpoints
     * protegidos: {@code mockMvc.perform(get(...).header("Authorization", bearer(id, Papel.ADMIN)))}.
     */
    protected String bearer(java.util.UUID usuarioId, Papel... papeis) {
        return "Bearer "
                + emissorDeToken.emitir(usuarioId, java.util.Set.of(papeis)).valor();
    }

    @BeforeEach
    void relogioEConfiguracaoNoEstadoPadrao() {
        sms.aguardarEnvios();
        sms.restaurar();
        email.restaurar();
        relogio.voltarAoPadrao();
        configuracaoNegocio.invalidarCache();
    }
}
