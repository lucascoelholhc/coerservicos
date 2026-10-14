package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * CORE-11: eventos sensíveis da conta gravados na log_auditoria (só inserção), na transação da ação,
 * com o ator, o papel e o IP agrupado. Em antes/depois só dado não pessoal: nenhuma linha tem o
 * celular, o e-mail, a senha, o código ou o token usados no teste.
 */
class AuditoriaDeEventosTest extends IntegracaoTest {

    private static final String SENHA_NOVA = "Outra-Senha-2026";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private PlatformTransactionManager transacoes;

    @Autowired
    private ServicoDeCodigoSms codigos;

    private ContasDeTeste contas;

    @BeforeEach
    void contas() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    /** Linhas da ação para a conta, como texto (para conferir que não há dado pessoal). */
    private List<Map<String, Object>> auditoria(String acao, UUID conta) {
        return jdbc.queryForList(
                "SELECT ator_id, ator_papel, entidade, entidade_id, antes::text AS antes, depois::text AS depois,"
                        + " host(ip) AS ip, masklen(ip) AS mascara FROM log_auditoria"
                        + " WHERE acao = ? AND entidade_id = ? ORDER BY id",
                acao,
                conta);
    }

    private static void semDadoPessoal(List<Map<String, Object>> linhas, Conta conta, String... segredos) {
        String texto = linhas.toString();
        assertThat(texto)
                .doesNotContain(conta.celular())
                .doesNotContain(SENHA_NOVA)
                .doesNotContain(ContasDeTeste.SENHA);
        if (conta.email() != null) {
            assertThat(texto).doesNotContain(conta.email());
        }
        for (String segredo : segredos) {
            assertThat(texto).doesNotContain(segredo);
        }
    }

    @Test
    @DisplayName("senha.trocar: ator é quem trocou, com o papel e o IP")
    void trocarSenha() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        mockMvc.perform(post("/api/auth/senha/trocar")
                        .with(requisicao -> {
                            requisicao.setRemoteAddr("203.0.113.70");
                            return requisicao;
                        })
                        .header("Authorization", bearer(conta.id(), Papel.CLIENTE))
                        .header("Origin", ORIGEM_DO_FRONT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + ContasDeTeste.SENHA + "\",\"novaSenha\":\"" + SENHA_NOVA
                                + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> linhas = auditoria("senha.trocar", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_id", conta.id())
                .containsEntry("ator_papel", "CLIENTE")
                .containsEntry("entidade", "usuario")
                .containsEntry("ip", "203.0.113.70")
                .containsEntry("depois", "{\"outrasSessoes\": \"revogadas\"}");
        semDadoPessoal(linhas, conta);
    }

    @Test
    @DisplayName("senha.redefinir pelo código SMS: registra a via, sem o código")
    void redefinirSenha() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        mockMvc.perform(post("/api/auth/senha/esqueci")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + conta.celular() + "\"}"))
                .andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();

        mockMvc.perform(post("/api/auth/senha/redefinir")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + conta.celular() + "\",\"codigo\":\"" + codigo
                                + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> linhas = auditoria("senha.redefinir", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_id", conta.id())
                .containsEntry("depois", "{\"via\": \"sms\"}");
        semDadoPessoal(linhas, conta, codigo);
    }

    @Test
    @DisplayName("mfa.ligar e mfa.desligar: antes e depois do estado")
    void mfa() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        String bearer = bearer(conta.id(), Papel.CLIENTE);
        String ligar = definirMfa(bearer, conta, true);
        relogio.avancar(Duration.ofSeconds(61));
        String desligar = definirMfa(bearer, conta, false);

        List<Map<String, Object>> ligado = auditoria("mfa.ligar", conta.id());
        List<Map<String, Object>> desligado = auditoria("mfa.desligar", conta.id());
        assertThat(ligado).hasSize(1);
        assertThat(ligado.getFirst())
                .containsEntry("ator_id", conta.id())
                .containsEntry("antes", "{\"ativo\": false}")
                .containsEntry("depois", "{\"ativo\": true}");
        assertThat(desligado).hasSize(1);
        assertThat(desligado.getFirst()).containsEntry("depois", "{\"ativo\": false}");
        semDadoPessoal(ligado, conta, ligar);
        semDadoPessoal(desligado, conta, desligar);
    }

    private String definirMfa(String bearer, Conta conta, boolean ativo) throws Exception {
        mockMvc.perform(post("/api/contas/eu/mfa/codigo").header("Authorization", bearer))
                .andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        mockMvc.perform(post("/api/contas/eu/mfa")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":" + ativo + ",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isNoContent());
        return codigo;
    }

    @Test
    @DisplayName("sessao.sair-de-todos: IPv6 gravado agrupado por /64, como no rate limit")
    void sairDeTodos() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);

        mockMvc.perform(post("/api/auth/sair-de-todos")
                        .with(requisicao -> {
                            requisicao.setRemoteAddr("2001:db8:1:2:3:4:5:6");
                            return requisicao;
                        })
                        .header("Authorization", bearer(conta.id(), Papel.CLIENTE))
                        .header("Origin", ORIGEM_DO_FRONT))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> linhas = auditoria("sessao.sair-de-todos", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_id", conta.id())
                .containsEntry("ip", "2001:db8:1:2::")
                .containsEntry("mascara", 64);
    }

    @Test
    @DisplayName("sessao.reuso-refresh: a família revogada é registrada pelo sistema, sem o token")
    void reusoDoRefresh() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        String refresh = LoginTest.refreshDoCookie(mockMvc.perform(post("/api/auth/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + conta.celular() + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());
        String sucessor = LoginTest.refreshDoCookie(
                renovar(refresh).andExpect(status().isOk()).andReturn().getResponse());
        relogio.avancar(Duration.ofSeconds(31));

        renovar(refresh).andExpect(status().isUnauthorized());

        List<Map<String, Object>> linhas = auditoria("sessao.reuso-refresh", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_id", null)
                .containsEntry("ator_papel", "SISTEMA")
                .containsEntry("depois", "{\"familia\": \"revogada\"}");
        semDadoPessoal(linhas, conta, refresh, sucessor);
    }

    private org.springframework.test.web.servlet.ResultActions renovar(String refresh) throws Exception {
        return mockMvc.perform(
                post("/api/auth/renovar").header("Origin", ORIGEM_DO_FRONT).cookie(new Cookie("coe_refresh", refresh)));
    }

    @Test
    @DisplayName("contato.recolocar (e-mail): só o canal, sem o endereço nem o link")
    void recolocarEmail() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET email = NULL, email_verificado_em = NULL WHERE id = ?", conta.id());
        String novo = "recolocado-" + conta.id().toString().substring(0, 8) + "@teste.coe.local";
        mockMvc.perform(put("/api/contas/eu/email")
                        .header("Authorization", bearer(conta.id(), Papel.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + novo + "\"}"))
                .andExpect(status().isAccepted());
        String token = email.ultimoToken(novo).orElseThrow();

        mockMvc.perform(post("/api/contas/email/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> linhas = auditoria("contato.recolocar", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_id", conta.id())
                .containsEntry("depois", "{\"canal\": \"email\"}");
        semDadoPessoal(linhas, conta, novo, token);
    }

    @Test
    @DisplayName("contato.recolocar (celular): só o canal, sem o número nem o código")
    void recolocarCelular() throws Exception {
        Conta conta = contas.criar(Papel.CLIENTE);
        jdbc.update("UPDATE usuario SET celular = NULL, celular_verificado_em = NULL WHERE id = ?", conta.id());
        String novo =
                "479000097" + String.format("%02d", Math.floorMod(conta.id().hashCode(), 100));
        String bearer = bearer(conta.id(), Papel.CLIENTE);
        mockMvc.perform(put("/api/contas/eu/celular")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + novo + "\"}"))
                .andExpect(status().isAccepted());
        String codigo = sms.ultimoCodigo(novo).orElseThrow();

        mockMvc.perform(post("/api/contas/eu/celular/confirmar")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> linhas = auditoria("contato.recolocar", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst()).containsEntry("depois", "{\"canal\": \"celular\"}");
        semDadoPessoal(linhas, conta, novo, codigo);
    }

    @Test
    @DisplayName("conta.bloqueio-codigos: registrado uma vez, no erro que atinge o limite")
    void bloqueioPorCodigos() {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        errarCincoVezes(conta);
        relogio.avancar(Duration.ofSeconds(61));
        errarCincoVezes(conta); // 10º erro: bloqueia

        assertThat(auditoria("conta.bloqueio-codigos", conta.id())).hasSize(1);
        relogio.avancar(Duration.ofSeconds(61));
        errarCincoVezes(conta); // já bloqueada: não registra de novo

        List<Map<String, Object>> linhas = auditoria("conta.bloqueio-codigos", conta.id());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst())
                .containsEntry("ator_papel", "SISTEMA")
                .containsEntry("depois", "{\"duracao\": \"PT1H\"}");
        semDadoPessoal(linhas, conta);
    }

    private void errarCincoVezes(Conta conta) {
        codigos.enviar(conta.id(), conta.celular(), FinalidadeSms.LOGIN, "203.0.113.71");
        String certo = sms.ultimoCodigo(conta.celular()).orElseThrow();
        String errado = certo.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            codigos.conferir(conta.celular(), FinalidadeSms.LOGIN, errado);
        }
    }

    @Test
    @DisplayName("log_auditoria continua só de inserção para o coe_app (sem UPDATE, DELETE nem TRUNCATE)")
    void soInsercao() {
        TransactionTemplate transacao = new TransactionTemplate(transacoes);
        for (String comando :
                List.of("UPDATE log_auditoria SET acao = 'x'", "DELETE FROM log_auditoria", "TRUNCATE log_auditoria")) {
            assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL ROLE coe_app");
                        jdbc.execute(comando);
                    }))
                    .as(comando)
                    .rootCause()
                    .isInstanceOfSatisfying(
                            org.postgresql.util.PSQLException.class,
                            erro -> assertThat(erro.getSQLState()).isEqualTo("42501")); // insufficient_privilege
        }
    }
}
