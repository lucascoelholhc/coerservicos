package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.coe.servicos.IntegracaoTest;

/** Código SMS (CORE-04): 6 dígitos, HMAC no banco, 5 min, uso único, 5 tentativas, limite de envios. */
class ServicoDeCodigoSmsTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(60_000);

    @Autowired
    ServicoDeCodigoSms codigos;

    @Autowired
    JdbcTemplate jdbc;

    private String novoCelular() {
        return String.valueOf(47_900_000_000L + SEQUENCIA.incrementAndGet());
    }

    private UUID usuarioCom(String celular) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, celular, email, senha_hash) VALUES ('Pessoa SMS', ?, ?, 'hash') RETURNING id",
                UUID.class,
                celular,
                "sms" + celular + "@teste.coe.local");
    }

    private String enviar(UUID usuario, String celular) {
        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                .isTrue();
        return sms.ultimoCodigo(celular).orElseThrow();
    }

    @Test
    @DisplayName("código de 6 dígitos por SMS; no banco só o HMAC de 32 bytes")
    void enviaEGuardaSoOHmac() {
        String celular = novoCelular();
        String codigo = enviar(usuarioCom(celular), celular);

        assertThat(codigo).matches("\\d{6}");
        byte[] guardado =
                jdbc.queryForObject("SELECT codigo_hmac FROM codigo_sms WHERE celular = ?", byte[].class, celular);
        assertThat(guardado).hasSize(32).isNotEqualTo(codigo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("certo confere uma vez só (uso único)")
    void certoUmaVez() {
        String celular = novoCelular();
        String codigo = enviar(usuarioCom(celular), celular);

        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, codigo)).isTrue();
        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, codigo)).isFalse();
    }

    @Test
    @DisplayName("errado não confere e conta a tentativa; finalidade errada não confere")
    void erradoConta() {
        String celular = novoCelular();
        String codigo = enviar(usuarioCom(celular), celular);
        String errado = codigo.equals("000000") ? "111111" : "000000";

        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, errado)).isFalse();
        assertThat(codigos.conferir(celular, FinalidadeSms.VERIFICAR_CELULAR, codigo))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT tentativas FROM codigo_sms WHERE celular = ?", Integer.class, celular))
                .isOne();
        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, codigo)).isTrue();
    }

    @Test
    @DisplayName("vencido (5 min) não confere")
    void vencido() {
        String celular = novoCelular();
        String codigo = enviar(usuarioCom(celular), celular);
        relogio.avancar(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, codigo)).isFalse();
    }

    @Test
    @DisplayName("na 5ª tentativa errada o código é invalidado (nem o certo vale depois)")
    void quintaTentativaInvalida() {
        String celular = novoCelular();
        String codigo = enviar(usuarioCom(celular), celular);
        String errado = codigo.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, errado)).isFalse();
        }

        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, codigo)).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT invalidado_em IS NOT NULL FROM codigo_sms WHERE celular = ?", Boolean.class, celular))
                .isTrue();
    }

    @Test
    @DisplayName("um código novo invalida o anterior")
    void novoInvalidaAnterior() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        String primeiro = enviar(usuario, celular);
        relogio.avancar(Duration.ofSeconds(61));
        String segundo = enviar(usuario, celular);

        assertThat(primeiro).isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM codigo_sms WHERE celular = ? AND invalidado_em IS NOT NULL",
                        Integer.class,
                        celular))
                .as("o primeiro foi invalidado")
                .isOne();
        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, segundo)).isTrue();
    }

    @Test
    @DisplayName("reenvio depois de o código vencer (o vencido é invalidado antes de inserir)")
    void reenvioDepoisDeVencer() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        enviar(usuario, celular);
        relogio.avancar(Duration.ofMinutes(10));

        String novo = enviar(usuario, celular);

        assertThat(codigos.conferir(celular, FinalidadeSms.LOGIN, novo)).isTrue();
    }

    @Test
    @DisplayName("no máximo 1 envio a cada 60 s por celular")
    void umPorMinuto() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        enviar(usuario, celular);
        relogio.avancar(Duration.ofSeconds(59));

        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                .isFalse();
        assertThat(sms.para(celular)).hasSize(1);
    }

    @Test
    @DisplayName("a espera de 60 s é por finalidade: outra finalidade no mesmo minuto sai")
    void esperaPorFinalidade() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        enviar(usuario, celular);

        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.CONFIGURAR_MFA, "127.0.0.1"))
                .isTrue();
        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                .isFalse();
    }

    @Test
    @DisplayName("o teto de 5 por hora soma as finalidades: a 6ª, misturando, não sai")
    void tetoSomaAsFinalidades() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        for (int i = 0; i < 3; i++) {
            assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                    .isTrue();
            if (i < 2) {
                assertThat(codigos.enviar(usuario, celular, FinalidadeSms.CONFIGURAR_MFA, "127.0.0.1"))
                        .isTrue();
            }
            relogio.avancar(Duration.ofSeconds(61));
        }

        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.CONFIGURAR_MFA, "127.0.0.1"))
                .isFalse();
    }

    @Test
    @DisplayName("no máximo 5 envios por hora por celular")
    void cincoPorHora() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        for (int i = 0; i < 5; i++) {
            enviar(usuario, celular);
            relogio.avancar(Duration.ofSeconds(61));
        }

        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                .isFalse();
        relogio.avancar(Duration.ofHours(1));
        assertThat(codigos.enviar(usuario, celular, FinalidadeSms.LOGIN, "127.0.0.1"))
                .isTrue();
    }

    @Test
    @DisplayName("desafio do MFA: confere com o código certo uma vez e devolve o dono")
    void desafio() {
        String celular = novoCelular();
        UUID usuario = usuarioCom(celular);
        String desafio = codigos.emitirDesafio(usuario, celular, "127.0.0.1").orElseThrow();
        String codigo = sms.ultimoCodigo(celular).orElseThrow();

        assertThat(codigos.conferirDesafio("outro-desafio", codigo)).isEmpty();
        assertThat(codigos.conferirDesafio(desafio, codigo)).contains(usuario);
        assertThat(codigos.conferirDesafio(desafio, codigo)).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT octet_length(desafio_hash) FROM codigo_sms WHERE celular = ?", Integer.class, celular))
                .isEqualTo(32);
    }
}
