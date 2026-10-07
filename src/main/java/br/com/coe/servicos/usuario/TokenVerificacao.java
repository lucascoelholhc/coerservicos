package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Link por e-mail ou comprovante de posse (tabela token_verificacao, V14): só o SHA-256 do token,
 * destino normalizado, datas do Clock. A aplicação só muda o uso e a invalidação, uma vez.
 */
@Entity
@Table(name = "token_verificacao")
@DynamicUpdate
public class TokenVerificacao implements Persistable<UUID> {

    static final String CANAL_EMAIL = "email";
    static final String CANAL_CELULAR = "celular";

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String canal;

    @Column(nullable = false)
    private String destino;

    @Column(nullable = false)
    private String finalidade;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "invalidado_em")
    private Instant invalidadoEm;

    @Transient
    private boolean novo = true;

    protected TokenVerificacao() {}

    @SuppressWarnings("java:S107") // espelha as colunas da tabela
    static TokenVerificacao novo(
            UUID usuarioId,
            String canal,
            String destino,
            String finalidade,
            byte[] tokenHash,
            Instant agora,
            Instant expiraEm) {
        TokenVerificacao token = new TokenVerificacao();
        token.id = UUID.randomUUID();
        token.usuarioId = usuarioId;
        token.canal = canal;
        token.destino = destino;
        token.finalidade = finalidade;
        token.tokenHash = tokenHash.clone();
        token.criadoEm = agora;
        token.expiraEm = expiraEm;
        return token;
    }

    boolean valeEm(Instant agora) {
        return usadoEm == null && invalidadoEm == null && expiraEm.isAfter(agora);
    }

    void usar(Instant agora) {
        this.usadoEm = agora;
    }

    void invalidar(Instant agora) {
        this.invalidadoEm = agora;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getCanal() {
        return canal;
    }

    String getDestino() {
        return destino;
    }

    String getFinalidade() {
        return finalidade;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostPersist
    @PostLoad
    void marcarComoGravado() {
        novo = false;
    }
}
