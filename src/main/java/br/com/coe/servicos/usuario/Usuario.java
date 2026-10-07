package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Conta de quem entra no sistema (tabela usuario). Nunca sai da API: use DTOs. */
@Entity
@Table(name = "usuario")
public class Usuario {

    static final String ATIVO = "ativo";
    static final String SUSPENSO = "suspenso";
    static final String EXCLUIDO = "excluido";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String celular;

    @Column(columnDefinition = "citext")
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 8)
    private String cep;

    @Column(nullable = false)
    private String status;

    @Column(name = "celular_verificado_em")
    private Instant celularVerificadoEm;

    @Column(name = "email_verificado_em")
    private Instant emailVerificadoEm;

    @Column(name = "mfa_sms_ativo", nullable = false)
    private boolean mfaSmsAtivo;

    @Version
    private Long versao;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papel", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "papel", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    protected Usuario() {}

    /** Cliente novo: ativo, celular ainda não confirmado (CORE-04) e sem cidade (pendente). */
    static Usuario novoCliente(String nome, String celular, String email, String senhaHash, String cep) {
        Usuario usuario = new Usuario();
        usuario.id = UUID.randomUUID();
        usuario.nome = nome;
        usuario.celular = celular;
        usuario.email = email;
        usuario.senhaHash = senhaHash;
        usuario.cep = cep;
        usuario.status = ATIVO;
        usuario.papeis.add(Papel.CLIENTE);
        return usuario;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }

    /** Hash da senha; nulo em conta excluída (anonimizada). Nunca sai do serviço de login. */
    String getSenhaHash() {
        return senhaHash;
    }

    String getEmail() {
        return email;
    }

    String getCelular() {
        return celular;
    }

    boolean isCelularConfirmado() {
        return celularVerificadoEm != null;
    }

    String getStatus() {
        return status;
    }

    boolean isMfaSmsAtivo() {
        return mfaSmsAtivo;
    }

    /** Celular confirmado por código SMS (RN08); a primeira confirmação vale. */
    void confirmarCelular(Instant agora) {
        if (celularVerificadoEm == null) {
            celularVerificadoEm = agora;
        }
    }

    boolean isEmailConfirmado() {
        return emailVerificadoEm != null;
    }

    /** E-mail confirmado por link (RN61); a primeira confirmação vale. */
    void confirmarEmail(Instant agora) {
        if (emailVerificadoEm == null) {
            emailVerificadoEm = agora;
        }
    }

    /** O banco exige celular confirmado para ligar (ck_usuario_mfa_celular). */
    void definirMfa(boolean ativo) {
        mfaSmsAtivo = ativo;
    }
}
