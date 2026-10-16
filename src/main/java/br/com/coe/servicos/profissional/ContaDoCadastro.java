package br.com.coe.servicos.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Dados da conta de quem se cadastra: nome e celular vêm daqui, nunca redigitados (DOM-02). */
@Component
class ContaDoCadastro {

    record Conta(String nome, boolean celularConfirmado, String papelPrincipal) {}

    private static final List<String> PRIORIDADE = List.of("ADMIN", "PROFISSIONAL", "CLIENTE");

    private final JdbcTemplate jdbc;

    ContaDoCadastro(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Conta de(UUID usuario) {
        List<String> papeis =
                jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, usuario);
        String principal =
                PRIORIDADE.stream().filter(papeis::contains).findFirst().orElse("CLIENTE");
        return jdbc.queryForObject(
                "SELECT nome, celular_verificado_em IS NOT NULL AS confirmado FROM usuario WHERE id = ?",
                (linha, n) -> new Conta(linha.getString("nome"), linha.getBoolean("confirmado"), principal),
                usuario);
    }

    /** Celular confirmado da conta, para o aviso da verificação (DOM-03); não confirmado = vazio. */
    Optional<String> celular(UUID usuario) {
        return jdbc
                .queryForList(
                        "SELECT celular FROM usuario WHERE id = ? AND celular IS NOT NULL"
                                + " AND celular_verificado_em IS NOT NULL",
                        String.class,
                        usuario)
                .stream()
                .findFirst();
    }

    /** O papel PROFISSIONAL nasce na aprovação (DOM-03); vale no próximo token (a renovação lê do banco). */
    void darPapelProfissional(UUID usuario) {
        jdbc.update(
                "INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'PROFISSIONAL') ON CONFLICT DO NOTHING",
                usuario);
    }
}
