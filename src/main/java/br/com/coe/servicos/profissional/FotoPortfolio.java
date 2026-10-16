package br.com.coe.servicos.profissional;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Foto de trabalho já feito (portfólio, RN22): legenda passa pela censura de contato. */
@Entity
@Table(name = "foto_portfolio")
class FotoPortfolio implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "profissional_id", nullable = false, updatable = false)
    private UUID profissionalId;

    @Column(name = "servico_id")
    private UUID servicoId;

    @Column(name = "chave_arquivo", nullable = false, updatable = false)
    private String chaveArquivo;

    private String legenda;

    @Column(nullable = false)
    private short ordem;

    @Transient
    private boolean novo = true;

    protected FotoPortfolio() {}

    FotoPortfolio(UUID profissionalId, UUID servicoId, String chaveArquivo, String legenda, short ordem) {
        this.id = UUID.randomUUID();
        this.profissionalId = profissionalId;
        this.servicoId = servicoId;
        this.chaveArquivo = chaveArquivo;
        this.legenda = legenda;
        this.ordem = ordem;
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

    void descrever(UUID servicoId, String legenda) {
        this.servicoId = servicoId;
        this.legenda = legenda;
    }

    UUID servicoId() {
        return servicoId;
    }

    String chaveArquivo() {
        return chaveArquivo;
    }

    String legenda() {
        return legenda;
    }
}
