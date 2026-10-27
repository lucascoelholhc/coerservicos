package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/** DB-14: falta do profissional (PA07, RN44c/RN44d) e a escolha do cliente sobre as outras diárias (RN44b). */
class MigracaoFaltasTest extends BancoIntegracaoTest {

    @Test
    @DisplayName("ocorrencia_profissional é só de inserção: UPDATE, DELETE e TRUNCATE recusados")
    void ocorrenciaSoDeInsercao() {
        for (String privilegio : new String[] {"UPDATE", "DELETE", "TRUNCATE"}) {
            assertThat(jdbc.queryForObject(
                            "SELECT has_table_privilege('coe_app', 'ocorrencia_profissional', ?)",
                            Boolean.class,
                            privilegio))
                    .as(privilegio)
                    .isFalse();
        }
        // UPDATE e DELETE numa linha de verdade são recusados pelo gatilho (DisputaTest)
        PSQLException erro = erroDoBanco(() -> jdbc.execute("TRUNCATE ocorrencia_profissional"));
        assertThat(erro.getSQLState()).isIn(INSUFFICIENT_PRIVILEGE, "P0001");
    }

    @Test
    @DisplayName("parâmetros das faltas na configuração: 2 faltas em 90 dias, e valor fora da faixa é recusado")
    void parametrosDasFaltas() {
        assertThat(jdbc.queryForObject(
                        "SELECT valor FROM configuracao_vigente WHERE chave = 'FALTAS_ALERTA'", String.class))
                .isEqualTo("2");
        assertThat(jdbc.queryForObject(
                        "SELECT valor FROM configuracao_vigente WHERE chave = 'FALTAS_JANELA_DIAS'", String.class))
                .isEqualTo("90");
        PSQLException erro = erroDoBanco(() -> jdbc.update("""
                INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde)
                VALUES ('FALTAS_ALERTA', '0', 'inteiro', 'teste', '2099-01-01')"""));
        assertConstraint(erro, CHECK_VIOLATION, "ck_configuracao_faltas");
    }

    @Test
    @DisplayName("a escolha das outras diárias só existe em disputa de falta decidida a favor do cliente")
    void escolhaDasDemaisDiarias() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_disputa_demais_diarias'",
                        Integer.class))
                .isEqualTo(1);
    }
}
