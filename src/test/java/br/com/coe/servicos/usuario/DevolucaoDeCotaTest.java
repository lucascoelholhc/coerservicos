package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta.Cota;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta.Origem;

/**
 * CORE-07: mensagem descartada pela fila cheia devolve a cota. Só a linha que gerou a mensagem; não
 * sobrescreve invalidação que já existia; não toca código já usado (M3/L5 da revisão da V16).
 */
class DevolucaoDeCotaTest extends IntegracaoTest {

    /** O banco não é limpo entre os testes: cada teste usa um celular e um e-mail só dele. */
    private static final java.util.concurrent.atomic.AtomicInteger SEQUENCIA =
            new java.util.concurrent.atomic.AtomicInteger(10);

    private final int numero = SEQUENCIA.getAndIncrement();
    private final String celular = "479000098" + numero;
    private final String email = "devolucao" + numero + "@teste.coe.local";
    private static final ServicoDeTokens.Email MODELO =
            new ServicoDeTokens.Email("/provar-email", "Teste", link -> "Abra: " + link);

    @Autowired
    private DevolucaoDeCotaNoBanco devolucao;

    @Autowired
    private ServicoDeCodigoSms codigos;

    @Autowired
    private ServicoDeTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Map<String, Object>> codigosDoCelular() {
        return jdbc.queryForList(
                "SELECT id, invalidado_em, descartado_em, usado_em FROM codigo_sms WHERE celular = ?"
                        + " ORDER BY criado_em",
                celular);
    }

    private UUID idDoCodigo(int ordem) {
        return (UUID) codigosDoCelular().get(ordem).get("id");
    }

    @Test
    @DisplayName("descarta o código e devolve a cota: outro pode ser pedido na hora (sem a espera de 60 s)")
    void devolveACota() {
        assertThat(codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50"))
                .isTrue();
        assertThat(codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50"))
                .isFalse();

        devolucao.devolver(new Cota(Origem.CODIGO_SMS, idDoCodigo(0)));

        Map<String, Object> descartado = codigosDoCelular().getFirst();
        assertThat(descartado.get("descartado_em")).isNotNull();
        assertThat(descartado.get("invalidado_em")).isNotNull();
        assertThat(codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50"))
                .isTrue();
    }

    @Test
    @DisplayName("código já substituído por um mais novo: marca o descarte e mantém a invalidação de antes")
    void naoSobrescreveAInvalidacao() {
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");
        relogio.avancar(Duration.ofSeconds(61));
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");
        Timestamp invalidadoAntes = (Timestamp) codigosDoCelular().getFirst().get("invalidado_em");
        relogio.avancar(Duration.ofSeconds(5));

        devolucao.devolver(new Cota(Origem.CODIGO_SMS, idDoCodigo(0)));

        Map<String, Object> primeiro = codigosDoCelular().getFirst();
        assertThat(primeiro.get("invalidado_em")).isEqualTo(invalidadoAntes);
        assertThat(primeiro.get("descartado_em")).isNotNull();
    }

    @Test
    @DisplayName("código já usado: não muda nada e não lança")
    void naoTocaCodigoUsado() {
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");
        UUID id = idDoCodigo(0);
        jdbc.update("UPDATE codigo_sms SET usado_em = criado_em WHERE id = ?", id);

        assertThatCode(() -> devolucao.devolver(new Cota(Origem.CODIGO_SMS, id)))
                .doesNotThrowAnyException();

        Map<String, Object> usado = codigosDoCelular().getFirst();
        assertThat(usado.get("descartado_em")).isNull();
        assertThat(usado.get("invalidado_em")).isNull();
    }

    @Test
    @DisplayName("só a linha da cota: as outras ficam como estão")
    void soALinhaDaCota() {
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");
        relogio.avancar(Duration.ofSeconds(61));
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");

        devolucao.devolver(new Cota(Origem.CODIGO_SMS, idDoCodigo(0)));

        assertThat(codigosDoCelular().get(1).get("descartado_em")).isNull();
        assertThat(codigosDoCelular().get(1).get("invalidado_em")).isNull();
        assertThat(codigosDoCelular().getFirst().get("descartado_em")).isNotNull();
    }

    @Test
    @DisplayName("descarte já marcado: devolver de novo não muda nem lança")
    void descarteRepetido() {
        codigos.enviar(null, celular, FinalidadeSms.COMPROVAR_POSSE, "203.0.113.50");
        Cota cota = new Cota(Origem.CODIGO_SMS, idDoCodigo(0));
        devolucao.devolver(cota);
        Object primeiraMarca = codigosDoCelular().getFirst().get("descartado_em");
        relogio.avancar(Duration.ofSeconds(5));

        assertThatCode(() -> devolucao.devolver(cota)).doesNotThrowAnyException();
        assertThat(codigosDoCelular().getFirst().get("descartado_em")).isEqualTo(primeiraMarca);
    }

    @Test
    @DisplayName("link por e-mail: descarta e devolve a cota do endereço")
    void link() {
        assertThat(tokens.enviarLink(null, email, FinalidadeToken.LINK_POSSE, Duration.ofMinutes(30), MODELO))
                .isTrue();
        UUID id = jdbc.queryForObject("SELECT id FROM token_verificacao WHERE destino = ?", UUID.class, email);

        devolucao.devolver(new Cota(Origem.TOKEN_VERIFICACAO, id));

        assertThat(jdbc.queryForObject(
                        "SELECT descartado_em IS NOT NULL AND invalidado_em IS NOT NULL FROM token_verificacao"
                                + " WHERE id = ?",
                        Boolean.class,
                        id))
                .isTrue();
        assertThat(tokens.enviarLink(null, email, FinalidadeToken.LINK_POSSE, Duration.ofMinutes(30), MODELO))
                .isTrue();
    }
}
