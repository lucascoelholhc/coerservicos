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

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Código SMS (tabela codigo_sms, V14): só o HMAC do código, datas do Clock. O banco só deixa a
 * aplicação mudar tentativas, uso e invalidação; por isso o UPDATE leva só as colunas alteradas.
 */
@Entity
@Table(name = "codigo_sms")
@DynamicUpdate
public class CodigoSms implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String celular;

    @Column(nullable = false)
    private String finalidade;

    @Column(name = "codigo_hmac", nullable = false)
    private byte[] codigoHmac;

    @Column(name = "desafio_hash")
    private byte[] desafioHash;

    @Column(nullable = false)
    private short tentativas;

    @Column(columnDefinition = "inet")
    @ColumnTransformer(write = "cast(? as inet)")
    private String ip;

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

    protected CodigoSms() {}

    @SuppressWarnings("java:S107") // espelha as colunas da tabela
    static CodigoSms novo(
            UUID usuarioId,
            String celular,
            String finalidade,
            byte[] codigoHmac,
            byte[] desafioHash,
            Instant agora,
            Instant expiraEm,
            String ip) {
        CodigoSms codigo = new CodigoSms();
        codigo.id = UUID.randomUUID();
        codigo.usuarioId = usuarioId;
        codigo.celular = celular;
        codigo.finalidade = finalidade;
        codigo.codigoHmac = codigoHmac.clone();
        codigo.desafioHash = desafioHash == null ? null : desafioHash.clone();
        codigo.criadoEm = agora;
        codigo.expiraEm = expiraEm;
        codigo.ip = AceiteTermos.normalizarIp(ip);
        return codigo;
    }

    boolean ativo() {
        return usadoEm == null && invalidadoEm == null;
    }

    boolean vencidoEm(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    void usar(Instant agora) {
        this.usadoEm = agora;
    }

    void invalidar(Instant agora) {
        this.invalidadoEm = agora;
    }

    /** Conta um erro; ao chegar no máximo, o código é invalidado (nem o certo vale depois). */
    void registrarErro(Instant agora, int maximoTentativas) {
        this.tentativas++;
        if (tentativas >= maximoTentativas) {
            invalidar(agora);
        }
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getCelular() {
        return celular;
    }

    String getFinalidade() {
        return finalidade;
    }

    byte[] getCodigoHmac() {
        return codigoHmac.clone();
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
