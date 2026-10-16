package br.com.coe.servicos.profissional;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Documento para verificação (RG, CNH, selfie, NR-10): só a chave do arquivo no bucket privado (RN16). */
@Entity
@Table(name = "documento_verificacao")
class DocumentoVerificacao implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "profissional_id", nullable = false, updatable = false)
    private UUID profissionalId;

    @Column(nullable = false, updatable = false)
    private String tipo;

    @Column(name = "chave_arquivo", nullable = false, updatable = false)
    private String chaveArquivo;

    @Column(nullable = false)
    private String status;

    @Column(name = "motivo_recusa")
    private String motivoRecusa;

    @Column(name = "analisado_por")
    private UUID analisadoPor;

    @Column(name = "analisado_em")
    private Instant analisadoEm;

    @Transient
    private boolean novo = true;

    protected DocumentoVerificacao() {}

    DocumentoVerificacao(UUID profissionalId, TipoDeDocumento tipo, String chaveArquivo) {
        this.id = UUID.randomUUID();
        this.profissionalId = profissionalId;
        this.tipo = tipo.codigo();
        this.chaveArquivo = chaveArquivo;
        this.status = "pendente";
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        novo = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    boolean pendente() {
        return "pendente".equals(status);
    }

    /** Conferido pelo admin na aprovação do cadastro (DOM-03). */
    void aprovar(UUID admin, Instant agora) {
        analisar("aprovado", admin, agora);
    }

    void recusar(UUID admin, Instant agora, String motivo) {
        analisar("recusado", admin, agora);
        this.motivoRecusa = motivo;
    }

    private void analisar(String novo, UUID admin, Instant agora) {
        this.status = novo;
        this.analisadoPor = admin;
        this.analisadoEm = agora;
    }

    String tipo() {
        return tipo;
    }

    String chaveArquivo() {
        return chaveArquivo;
    }

    String status() {
        return status;
    }
}
