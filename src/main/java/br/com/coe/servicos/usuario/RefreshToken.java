package br.com.coe.servicos.usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Sessão de um aparelho (tabela refresh_token, V12): só o hash do token, datas do Clock. O banco
 * só deixa mudar uso, revogação e sucessor, uma vez; por isso o UPDATE leva só as colunas alteradas.
 */
@Entity
@Table(name = "refresh_token")
@DynamicUpdate
public class RefreshToken implements Persistable<UUID> {

    private static final int TAMANHO_MAXIMO_APARELHO = 120;

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    private String aparelho;

    @Column(columnDefinition = "inet")
    @ColumnTransformer(write = "cast(? as inet)")
    private String ip;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "motivo_revogacao")
    private String motivoRevogacao;

    @Column(name = "substituido_por")
    private UUID substituidoPor;

    @Transient
    private boolean novo = true;

    protected RefreshToken() {}

    static RefreshToken novo(
            UUID usuarioId,
            UUID familiaId,
            byte[] tokenHash,
            Instant agora,
            Duration validade,
            String ip,
            String userAgent) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.usuarioId = usuarioId;
        token.familiaId = familiaId;
        token.tokenHash = tokenHash.clone();
        token.criadoEm = agora;
        token.expiraEm = agora.plus(validade);
        token.ip = AceiteTermos.normalizarIp(ip);
        token.aparelho = resumirAparelho(userAgent);
        return token;
    }

    private static String resumirAparelho(String userAgent) {
        String limpo = AceiteTermos.normalizarUserAgent(userAgent);
        if (limpo == null || limpo.length() <= TAMANHO_MAXIMO_APARELHO) {
            return limpo;
        }
        return limpo.substring(0, TAMANHO_MAXIMO_APARELHO);
    }

    void usar(Instant agora, UUID sucessor) {
        this.usadoEm = agora;
        this.substituidoPor = sucessor;
    }

    boolean revogado() {
        return revogadoEm != null;
    }

    boolean vencidoEm(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    Instant getUsadoEm() {
        return usadoEm;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    UUID getFamiliaId() {
        return familiaId;
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
