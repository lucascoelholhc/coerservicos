package br.com.coe.servicos.profissional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import br.com.coe.servicos.compartilhado.cripto.ChavePixCifradaConverter;
import br.com.coe.servicos.compartilhado.cripto.CpfCifradoConverter;
import br.com.coe.servicos.compartilhado.cripto.HmacDeCpf;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;

/**
 * Perfil do profissional (V4/V11): nasce em rascunho no cadastro de 11 etapas (DOM-02), vai para análise
 * quando completo e só fica ativo na aprovação (DOM-03). CPF e chave Pix cifrados (CORE-10); o CPF também
 * como HMAC único, sempre gravados juntos por {@link #definirCpf}.
 */
@Entity
@Table(name = "profissional")
class Profissional implements Persistable<UUID> {

    static final String RASCUNHO = "rascunho";
    static final String EM_ANALISE = "em_analise";
    static final String CORRECAO_PEDIDA = "correcao_pedida";
    static final String ATIVO = "ativo";
    static final String RECUSADO = "recusado";
    static final String NR10_NAO_TEM = "nao_tem";
    static final String NR10_EM_ANALISE = "em_analise";
    static final String NR10_VERIFICADO = "verificado";
    static final String NR10_RECUSADO = "recusado";

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private String status;

    @Column(name = "etapa_cadastro", nullable = false)
    private short etapaCadastro = 1;

    @Column(name = "profissao_principal_id")
    private UUID profissaoPrincipalId;

    @ElementCollection
    @CollectionTable(name = "profissional_profissao", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "profissao_id")
    private Set<UUID> outrasProfissoes = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "profissional_servico", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "servico_id")
    private Set<UUID> servicos = new HashSet<>();

    @Column(name = "experiencia_anos")
    private Short experienciaAnos;

    private String ferramentas;

    private String horario;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> frases = new ArrayList<>();

    private String bio;

    @Column(name = "valor_diaria")
    private Dinheiro valorDiaria;

    @Column(name = "aceita_meia_diaria", nullable = false)
    private boolean aceitaMeiaDiaria;

    @Column(name = "valor_meia_diaria")
    private Dinheiro valorMeiaDiaria;

    @Column(name = "cidade_base_id")
    private UUID cidadeBaseId;

    @Column(name = "raio_km")
    private Short raioKm;

    @ElementCollection
    @CollectionTable(name = "profissional_cidade", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "cidade_id")
    private Set<UUID> outrasCidades = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "disponibilidade_semanal", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "dia_semana")
    private Set<Short> dias = new HashSet<>();

    @Convert(converter = CpfCifradoConverter.class)
    @Column(name = "cpf_cifrado")
    private Cpf cpf;

    @Column(name = "cpf_hash")
    private byte[] cpfHash;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "chave_pix_tipo")
    private String chavePixTipo;

    @Convert(converter = ChavePixCifradaConverter.class)
    @Column(name = "chave_pix_cifrada")
    private String chavePix;

    @Column(nullable = false)
    private boolean mei;

    @Column(name = "nr10_status", nullable = false)
    private String nr10Status = NR10_NAO_TEM;

    @Column(name = "enviado_analise_em")
    private Instant enviadoAnaliseEm;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "motivo_correcao")
    private String motivoCorrecao;

    @Column(name = "motivo_recusa")
    private String motivoRecusa;

    @Version
    private long versao;

    /** Id gerado aqui: sem isso o Spring Data faria um SELECT (merge) antes de cada INSERT. */
    @Transient
    private boolean novo = true;

    protected Profissional() {}

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

    /** Rascunho e correção pedida podem ser editados; em análise, ativo e afins, não. */
    boolean editavel() {
        return RASCUNHO.equals(status) || CORRECAO_PEDIDA.equals(status);
    }

    void definirTrabalho(UUID principal, Set<UUID> outras) {
        this.profissaoPrincipalId = principal;
        trocar(this.outrasProfissoes, outras);
    }

    /** CPF cifrado e HMAC sempre juntos: um sem o outro quebraria a unicidade (revisão da CORE-10). */
    void definirCpf(Cpf cpf, HmacDeCpf hmac, LocalDate nascimento) {
        this.cpf = cpf;
        this.cpfHash = hmac.de(cpf);
        this.dataNascimento = nascimento;
    }

    /** Confere que o HMAC gravado é o do CPF decifrado (detecta cifra copiada de outra linha). */
    boolean cpfConfere(HmacDeCpf hmac) {
        return cpf == null || Arrays.equals(cpfHash, hmac.de(cpf));
    }

    void definirServicos(Set<UUID> servicos) {
        trocar(this.servicos, servicos);
    }

    void definirExperiencia(short anos, String ferramentas, boolean mei) {
        this.experienciaAnos = anos;
        this.ferramentas = ferramentas;
        this.mei = mei;
    }

    void definirValor(Dinheiro diaria, Dinheiro meia, String horario) {
        this.valorDiaria = diaria;
        this.aceitaMeiaDiaria = meia != null;
        this.valorMeiaDiaria = meia;
        this.horario = horario;
    }

    void definirOnde(UUID cidadeBase, short raio, Set<UUID> outras) {
        this.cidadeBaseId = cidadeBase;
        this.raioKm = raio;
        trocar(this.outrasCidades, outras);
    }

    void definirDias(Set<Short> dias) {
        trocar(this.dias, dias);
    }

    /**
     * Muda a coleção no lugar: o Hibernate grava só a diferença (trocar a instância apagaria e regravaria todas as
     * linhas da tabela de ligação a cada etapa).
     */
    private static <T> void trocar(Set<T> atual, Set<T> nova) {
        atual.retainAll(nova);
        atual.addAll(nova);
    }

    void definirSobre(List<String> frases, String bio) {
        this.frases = new ArrayList<>(frases);
        this.bio = bio;
    }

    void definirPix(String tipo, String chave) {
        this.chavePixTipo = tipo;
        this.chavePix = chave;
    }

    void enviarParaAnalise(Instant agora, boolean temCertificadoNr10) {
        this.status = EM_ANALISE;
        this.enviadoAnaliseEm = agora;
        this.motivoCorrecao = null;
        this.etapaCadastro = 11;
        // o certificado pode ter saído numa correção: sem ele, o NR-10 volta a não existir
        this.nr10Status = temCertificadoNr10 ? NR10_EM_ANALISE : NR10_NAO_TEM;
    }

    /** Aprovado pelo admin (DOM-03): vira ativo; {@code decisaoNr10} só quando havia certificado em análise. */
    void aprovar(UUID admin, Instant agora, String decisaoNr10) {
        this.status = ATIVO;
        this.aprovadoEm = agora;
        this.aprovadoPor = admin;
        if (decisaoNr10 != null) {
            this.nr10Status = decisaoNr10;
        }
    }

    /** Volta a editar com o motivo (RN13); o reenvio limpa o motivo. */
    void pedirCorrecao(String motivo) {
        this.status = CORRECAO_PEDIDA;
        this.motivoCorrecao = motivo;
    }

    void recusar(String motivo) {
        this.status = RECUSADO;
        this.motivoRecusa = motivo;
    }

    void definirEtapaAtual(int etapa) {
        this.etapaCadastro = (short) etapa;
    }

    /** "Sobre você" como aparece no perfil: frases prontas + texto livre. */
    String textoSobre() {
        String juntas = String.join(" ", frases);
        String texto = bio == null ? "" : bio.strip();
        return (juntas + " " + texto).strip();
    }

    UUID id() {
        return id;
    }

    UUID usuarioId() {
        return usuarioId;
    }

    String nr10Status() {
        return nr10Status;
    }

    Instant enviadoAnaliseEm() {
        return enviadoAnaliseEm;
    }

    String status() {
        return status;
    }

    UUID profissaoPrincipalId() {
        return profissaoPrincipalId;
    }

    Set<UUID> outrasProfissoes() {
        return Set.copyOf(outrasProfissoes);
    }

    Set<UUID> servicos() {
        return Set.copyOf(servicos);
    }

    Short experienciaAnos() {
        return experienciaAnos;
    }

    String ferramentas() {
        return ferramentas;
    }

    boolean mei() {
        return mei;
    }

    String horario() {
        return horario;
    }

    List<String> frases() {
        return List.copyOf(frases);
    }

    String bio() {
        return bio;
    }

    Dinheiro valorDiaria() {
        return valorDiaria;
    }

    Dinheiro valorMeiaDiaria() {
        return valorMeiaDiaria;
    }

    UUID cidadeBaseId() {
        return cidadeBaseId;
    }

    Short raioKm() {
        return raioKm;
    }

    Set<UUID> outrasCidades() {
        return Set.copyOf(outrasCidades);
    }

    Set<Short> dias() {
        return Set.copyOf(dias);
    }

    Cpf cpf() {
        return cpf;
    }

    LocalDate dataNascimento() {
        return dataNascimento;
    }

    String chavePixTipo() {
        return chavePixTipo;
    }

    String chavePix() {
        return chavePix;
    }

    /** Nunca CPF, Pix nem nascimento no log. */
    @Override
    public String toString() {
        return "Profissional[id=" + id + ", status=" + status + "]";
    }
}
