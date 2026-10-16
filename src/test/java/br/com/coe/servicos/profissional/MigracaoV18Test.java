package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.coe.servicos.IntegracaoTest;

/** V18: um documento de cada tipo por profissional, garantido também no banco (revisão do DOM-02). */
class MigracaoV18Test extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("dois documentos do mesmo tipo no mesmo cadastro: o banco recusa")
    void documentoUnicoPorTipo() {
        UUID usuario = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('V18', ?, 'x') RETURNING id",
                UUID.class,
                "v18-" + UUID.randomUUID() + "@teste.coe.local");
        UUID profissional = jdbc.queryForObject(
                "INSERT INTO profissional (usuario_id) VALUES (?) RETURNING id", UUID.class, usuario);
        jdbc.update(
                "INSERT INTO documento_verificacao (profissional_id, tipo, chave_arquivo) VALUES (?, 'selfie', ?)",
                profissional,
                "selfie/" + UUID.randomUUID() + ".jpg");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO documento_verificacao (profissional_id, tipo, chave_arquivo) VALUES (?, 'selfie', ?)",
                        profissional,
                        "selfie/" + UUID.randomUUID() + ".jpg"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_documento_profissional_tipo");
    }
}
