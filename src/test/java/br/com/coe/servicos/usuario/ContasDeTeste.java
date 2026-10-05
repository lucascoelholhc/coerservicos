package br.com.coe.servicos.usuario;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria contas direto no banco para os testes de login (celulares fictícios 4790000xxxx). Grava de
 * verdade (sem rollback): cada conta tem celular e e-mail únicos.
 */
final class ContasDeTeste {

    static final String SENHA = "Casa-Azul-2026";
    /** Hash do seed local (bcrypt $2b$ custo 10, sem prefixo) da senha coe-local-123. */
    static final String HASH_DO_SEED = "$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y";

    static final String SENHA_DO_SEED = "coe-local-123";

    private static final AtomicLong SEQUENCIA = new AtomicLong(5_000);
    private static volatile String hashDaSenha;

    record Conta(UUID id, String celular, String email, String nome) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder codificador;

    ContasDeTeste(JdbcTemplate jdbc, PasswordEncoder codificador) {
        this.jdbc = jdbc;
        this.codificador = codificador;
    }

    Conta criar(Papel... papeis) {
        return criar("ativo", false, hashDaSenha(), papeis);
    }

    Conta criar(String status, boolean mfa, Papel... papeis) {
        return criar(status, mfa, hashDaSenha(), papeis);
    }

    Conta criarComHash(String hash, Papel... papeis) {
        return criar("ativo", false, hash, papeis);
    }

    private Conta criar(String status, boolean mfa, String hash, Papel... papeis) {
        long numero = SEQUENCIA.incrementAndGet();
        String celular = String.valueOf(47_900_000_000L + numero);
        String email = "conta" + numero + "@teste.coe.local";
        String nome = "Pessoa " + numero;
        UUID id = jdbc.queryForObject("""
                INSERT INTO usuario (nome, celular, email, senha_hash, status, mfa_sms_ativo, excluido_em)
                VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'excluido' THEN now() END) RETURNING id
                """, UUID.class, nome, celular, email, hash, status, mfa, status);
        for (Papel papel : papeis) {
            jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, ?)", id, papel.name());
        }
        return new Conta(id, celular, email, nome);
    }

    private String hashDaSenha() {
        if (hashDaSenha == null) {
            hashDaSenha = codificador.encode(SENHA);
        }
        return hashDaSenha;
    }
}
