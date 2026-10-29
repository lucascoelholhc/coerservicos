package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;

/**
 * Primeiro admin em produção (DOM-11, lacuna de 26/10): uso único, nunca no perfil local, recusa com ADMIN existente,
 * senha nunca vem por variável (vai o link de redefinir), auditado pelo SISTEMA. O banco dos testes sempre tem admins:
 * o caminho da criação roda numa transação desfeita no fim, com os outros admins apagados só dentro dela.
 */
class BootstrapDoPrimeiroAdminTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(7_000);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ServicoDeTokens tokens;

    @Autowired
    private RegistroDeAuditoria auditoria;

    @Autowired
    private PlatformTransactionManager transacoes;

    @Autowired
    private TokenVerificacaoRepository linksGravados;

    private record Dados(String celular, String email) {}

    private static Dados dados() {
        long numero = SEQUENCIA.incrementAndGet();
        return new Dados(String.valueOf(47_900_000_000L + numero), "primeiro" + numero + "@teste.coe.local");
    }

    private BootstrapDoPrimeiroAdmin bootstrap(MockEnvironment ambiente, Dados dados) {
        return new BootstrapDoPrimeiroAdmin(
                ambiente,
                jdbc,
                codificador,
                tokens,
                auditoria,
                transacoes,
                null,
                "Primeira Admin",
                dados.celular(),
                dados.email(),
                false);
    }

    private BootstrapDoPrimeiroAdmin bootstrap(Dados dados) {
        return bootstrap(new MockEnvironment().withProperty("x", "y"), dados);
    }

    private long contasCom(String celular) {
        return jdbc.queryForObject("SELECT count(*) FROM usuario WHERE celular = ?", Long.class, celular);
    }

    @Test
    @DisplayName("nunca no perfil local: a aplicação não sobe com o bootstrap ligado")
    void nuncaNoLocal() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatThrownBy(() -> bootstrap(local, dados()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil local");
    }

    @Test
    @DisplayName("variável faltando ou inválida: não sobe, com mensagem clara e sem o valor")
    void variavelFaltando() {
        assertThatThrownBy(() -> bootstrap(new Dados("", "a@b.coe.local")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COE_BOOTSTRAP_ADMIN_CELULAR");
        assertThatThrownBy(() -> bootstrap(new Dados("4799", "a@b.coe.local")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("4799");
        assertThatThrownBy(() -> bootstrap(new Dados("47900001234", "sem-arroba")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COE_BOOTSTRAP_ADMIN_EMAIL");
    }

    @Test
    @DisplayName("já existe ADMIN: não faz nada (nem conta, nem auditoria)")
    void recusaComAdminExistente() {
        new ContasDeTeste(jdbc, codificador).criarConfirmada(Papel.ADMIN);
        Dados dados = dados();
        long auditoriasAntes =
                jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE acao = 'admin.bootstrap'", Long.class);
        assertThat(bootstrap(dados).executar()).isFalse();
        assertThat(contasCom(dados.celular())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE acao = 'admin.bootstrap'", Long.class))
                .isEqualTo(auditoriasAntes);
    }

    @Test
    @DisplayName(
            "sem nenhum ADMIN: cria a conta ADMIN com MFA, manda o link de criar a senha e audita; a segunda vez não faz nada")
    void criaUmaVezSo() {
        Dados dados = dados();
        BootstrapDoPrimeiroAdmin comando = bootstrap(dados);
        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("DELETE FROM usuario_papel WHERE papel = 'ADMIN'");

            assertThat(comando.executar()).isTrue();

            Map<String, Object> conta = jdbc.queryForMap("""
                    SELECT id, mfa_sms_ativo, celular_verificado_em IS NOT NULL AS celular_ok,
                           email_verificado_em IS NOT NULL AS email_ok, senha_hash
                      FROM usuario WHERE celular = ?""", dados.celular());
            UUID id = (UUID) conta.get("id");
            assertThat(conta).containsEntry("mfa_sms_ativo", true).containsEntry("celular_ok", true);
            assertThat(conta).containsEntry("email_ok", true);
            assertThat((String) conta.get("senha_hash")).startsWith("{bcrypt}");
            assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, id))
                    .containsExactly("ADMIN");
            // o token é gravado pelo JPA: a consulta do repositório descarrega antes de contar
            assertThat(linksGravados.findAll().stream()
                            .filter(link -> id.equals(link.getUsuarioId()))
                            .count())
                    .isEqualTo(1);
            assertThat(jdbc.queryForMap(
                            "SELECT ator_papel, antes::text || depois::text AS dados FROM log_auditoria"
                                    + " WHERE acao = 'admin.bootstrap' AND entidade_id = ?",
                            id))
                    .containsEntry("ator_papel", "SISTEMA")
                    .satisfies(linha -> assertThat(linha.get("dados").toString())
                            .doesNotContain(dados.celular())
                            .doesNotContain(dados.email()));

            assertThat(bootstrap(dados()).executar()).isFalse();
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM log_auditoria WHERE acao = 'admin.bootstrap' AND entidade_id = ?",
                            Long.class,
                            id))
                    .isEqualTo(1);
        });
        assertThat(contasCom(dados.celular())).isZero();
    }
}
