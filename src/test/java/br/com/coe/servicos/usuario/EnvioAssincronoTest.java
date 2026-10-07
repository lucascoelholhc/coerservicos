package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.usuario.ContasDeTeste.Conta;

/**
 * Envio assíncrono (decisão de 07/10): o SMS sai depois do commit, numa fila própria; a resposta
 * não espera o provedor, então o tempo não revela se o celular tem conta.
 */
@ExtendWith(OutputCaptureExtension.class)
class EnvioAssincronoTest extends IntegracaoTest {

    /** Provedor de 2 s e resposta abaixo de 1 s: folga para a máquina de CI, sem comparar os dois tempos. */
    private static final Duration PROVEDOR_LENTO = Duration.ofSeconds(2);

    private static final long LIMITE_DA_RESPOSTA_MS = 1_000;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    ServicoDeCodigoSms codigos;

    @Autowired
    PlatformTransactionManager transacoes;

    ContasDeTeste contas;

    @BeforeEach
    void preparar() {
        contas = new ContasDeTeste(jdbc, codificador);
    }

    private long pedirCodigoEmMs(String celular) throws Exception {
        long inicio = System.nanoTime();
        mockMvc.perform(post("/api/auth/codigo")
                        .header("Origin", ORIGEM_DO_FRONT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"" + celular + "\"}"))
                .andExpect(status().isAccepted());
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }

    @Test
    @DisplayName("a resposta do pedido de código não espera o provedor de SMS")
    void respostaNaoEsperaOEnvio() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        sms.atrasar(PROVEDOR_LENTO);

        long tempo = pedirCodigoEmMs(conta.celular());

        assertThat(tempo).isLessThan(LIMITE_DA_RESPOSTA_MS);
        assertThat(sms.ultimoCodigo(conta.celular())).as("o SMS chega depois").isPresent();
    }

    @Test
    @DisplayName("celular com conta e sem conta respondem rápido do mesmo jeito (provedor lento)")
    void tempoNaoRevelaAConta() throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        sms.atrasar(PROVEDOR_LENTO);

        long comConta = pedirCodigoEmMs(conta.celular());
        long semConta = pedirCodigoEmMs("47999997777");

        assertThat(comConta).isLessThan(LIMITE_DA_RESPOSTA_MS);
        assertThat(semConta).isLessThan(LIMITE_DA_RESPOSTA_MS);
    }

    @Test
    @DisplayName("rollback não envia nada (o envio é depois do commit)")
    void rollbackNaoEnvia() {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            codigos.enviar(conta.id(), conta.celular(), FinalidadeSms.LOGIN, "127.0.0.1");
            status.setRollbackOnly();
        });

        assertThat(sms.para(conta.celular())).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM codigo_sms WHERE celular = ?", Integer.class, conta.celular()))
                .isZero();
    }

    @Test
    @DisplayName("provedor fora do ar: a resposta é o mesmo 202 e o erro fica só no log, mascarado")
    void falhaDoProvedorNaoMudaAResposta(CapturedOutput saida) throws Exception {
        Conta conta = contas.criarConfirmada(Papel.CLIENTE);
        sms.falharProximo();

        pedirCodigoEmMs(conta.celular());

        assertThat(sms.para(conta.celular())).isEmpty();
        assertThat(saida.getAll())
                .contains("Falha ao enviar SMS para " + conta.celular().substring(0, 2) + "*****")
                .doesNotContain(conta.celular());
    }
}
