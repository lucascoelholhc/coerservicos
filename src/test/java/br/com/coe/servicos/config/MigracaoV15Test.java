package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * V15 (índices das contagens de envio e de falhas, dia 7b): cada consulta real usa um índice cujas
 * colunas e ordem batem com o WHERE. As tabelas do teste são pequenas (o planejador preferiria a
 * varredura), então o EXPLAIN roda com enable_seqscan desligado só nesta transação: o que se prova é
 * que o índice serve à consulta, não o custo.
 */
class MigracaoV15Test extends BancoIntegracaoTest {

    private static final String CELULAR = "'47900000951'";
    private static final String DESDE = "now() - interval '1 hour'";

    private UUID usuario;

    @BeforeEach
    void semVarredura() {
        usuario = fixtures.usuario();
        jdbc.execute("SET LOCAL enable_seqscan = off");
        // O ix_codigo_sms_usuario (V14, só usuario_id) também serviria às consultas da conta; sai só
        // nesta transação (desfeita no fim) para provar que o índice novo é que atende.
        jdbc.execute("DROP INDEX ix_codigo_sms_usuario");
    }

    private String plano(String consulta) {
        List<String> linhas = jdbc.queryForList("EXPLAIN " + consulta, String.class);
        return String.join("\n", linhas);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            envios por celular na hora (todas as finalidades) | SELECT count(*) FROM codigo_sms WHERE celular = :cel AND criado_em > :desde | ix_codigo_sms_celular
            envios por celular e finalidade (espera de 60 s) | SELECT count(*) FROM codigo_sms WHERE celular = :cel AND finalidade = 'login' AND criado_em > :desde | ix_codigo_sms_celular
            erros da conta (contador de falhas) | SELECT coalesce(sum(tentativas), 0) FROM codigo_sms WHERE usuario_id = :usuario AND criado_em > :desde | ix_codigo_sms_usuario_criado
            último erro da conta | SELECT max(criado_em) FROM codigo_sms WHERE usuario_id = :usuario AND tentativas > 0 AND criado_em > :desde | ix_codigo_sms_usuario_criado
            erros de prova de posse do número | SELECT coalesce(sum(tentativas), 0) FROM codigo_sms WHERE usuario_id IS NULL AND celular = :cel AND finalidade = 'comprovar_posse' AND criado_em > :desde | ix_codigo_sms_celular
            e-mails por endereço na hora | SELECT count(*) FROM token_verificacao WHERE destino = 'a@teste.coe.local' AND canal = 'email' AND finalidade <> 'comprovante_posse' AND criado_em > :desde | ix_token_verificacao_destino
            e-mails por endereço e finalidade (espera de 60 s) | SELECT count(*) FROM token_verificacao WHERE destino = 'a@teste.coe.local' AND canal = 'email' AND finalidade = 'confirmar_email' AND criado_em > :desde | ix_token_verificacao_destino
            """)
    @DisplayName("a consulta usa o índice certo")
    void consultaUsaOIndice(String descricao, String consulta, String indice) {
        String sql =
                consulta.replace(":cel", CELULAR).replace(":desde", DESDE).replace(":usuario", "'" + usuario + "'");

        assertThat(plano(sql)).as(descricao).containsPattern("\\b" + indice + "\\b");
    }
}
