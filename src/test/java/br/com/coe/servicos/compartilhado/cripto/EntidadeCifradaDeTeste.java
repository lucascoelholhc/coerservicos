package br.com.coe.servicos.compartilhado.cripto;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/**
 * Entidade só de teste sobre a tabela {@code profissional}, para provar que o Hibernate cria os conversores
 * pelo Spring (injeção da {@link CifraDeCampo}) antes de existir a entidade real (DOM-02, que a substitui).
 */
@Entity
@Table(name = "profissional")
class EntidadeCifradaDeTeste {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Convert(converter = CpfCifradoConverter.class)
    @Column(name = "cpf_cifrado")
    private Cpf cpf;

    @Column(name = "cpf_hash")
    private byte[] cpfHash;

    @Column(name = "chave_pix_tipo")
    private String chavePixTipo;

    @Convert(converter = ChavePixCifradaConverter.class)
    @Column(name = "chave_pix_cifrada")
    private String chavePix;

    protected EntidadeCifradaDeTeste() {}

    EntidadeCifradaDeTeste(UUID usuarioId, Cpf cpf, byte[] cpfHash, String chavePix) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.cpf = cpf;
        this.cpfHash = cpfHash;
        this.chavePixTipo = "email";
        this.chavePix = chavePix;
    }

    UUID id() {
        return id;
    }

    Cpf cpf() {
        return cpf;
    }

    String chavePix() {
        return chavePix;
    }
}
