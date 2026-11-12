package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.IntegracaoTest;

/**
 * A conferência da chave Pix pelo admin (RN15, PA02) só cai quando a chave muda de verdade: salvar o profissional por
 * outro motivo (aprovar, experiência, média) não regrava a chave cifrada (que muda a cada cifra, nonce novo).
 */
class ConferenciaDoPixTest extends IntegracaoTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfissionalRepository profissionais;

    @Autowired
    private TransactionTemplate transacao;

    private UUID perfil;

    @BeforeEach
    void conferido() {
        ProfissionaisDeTeste.Criado criado = ProfissionaisDeTeste.ativo(contexto);
        perfil = criado.profissional();
        jdbc.update(
                "UPDATE profissional SET pix_conferido_em = now(), pix_conferido_por = ? WHERE id = ?",
                criado.usuario(),
                perfil);
    }

    private boolean conferida() {
        return jdbc.queryForObject(
                "SELECT pix_conferido_em IS NOT NULL FROM profissional WHERE id = ?", Boolean.class, perfil);
    }

    @Test
    @DisplayName("salvar o profissional sem mexer no Pix mantém a conferência")
    void salvarSemMudarOPixMantem() {
        transacao.executeWithoutResult(status -> {
            Profissional p = profissionais.findById(perfil).orElseThrow();
            p.definirExperiencia((short) 10, "sim", false);
            profissionais.save(p);
        });

        assertThat(conferida()).isTrue();
    }

    @Test
    @DisplayName("trocar a chave Pix pela entidade desfaz a conferência")
    void trocarAChaveDesfaz() {
        transacao.executeWithoutResult(status -> {
            Profissional p = profissionais.findById(perfil).orElseThrow();
            p.definirPix("aleatoria", UUID.randomUUID().toString());
            profissionais.save(p);
        });

        assertThat(conferida()).isFalse();
    }
}
