package br.com.coe.servicos.usuario;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

/** Prova do aceite dos termos: versão, IP e navegador (RNF18). Nunca é apagada. */
@Entity
@Table(name = "aceite_termos")
public class AceiteTermos implements Persistable<UUID> {

    private static final int TAMANHO_MAXIMO_USER_AGENT = 500;
    private static final int TAMANHO_MAXIMO_IP = 45;
    private static final Pattern IPV4 =
            Pattern.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");
    private static final Pattern CONTROLE = Pattern.compile("\\p{Cntrl}");

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "versao_termos", nullable = false)
    private String versaoTermos;

    @Column(columnDefinition = "inet")
    @ColumnTransformer(write = "cast(? as inet)")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    /** Id gerado aqui: sem isso o Spring Data faria um SELECT (merge) antes de cada INSERT. */
    @Transient
    private boolean novo = true;

    protected AceiteTermos() {}

    AceiteTermos(UUID usuarioId, String versaoTermos, String ip, String userAgent) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.versaoTermos = versaoTermos;
        this.ip = normalizarIp(ip);
        this.userAgent = normalizarUserAgent(userAgent);
    }

    /**
     * IP literal (v4 ou v6) ou nulo. Tira o scope id do IPv6 ("fe80::1%eth0"), que o inet recusa. Só
     * texto com ":" chega ao InetAddress, que então o trata como literal e nunca consulta o DNS.
     */
    static String normalizarIp(String ip) {
        if (ip == null) {
            return null;
        }
        int escopo = ip.indexOf('%');
        String semEscopo = escopo >= 0 ? ip.substring(0, escopo) : ip;
        if (IPV4.matcher(semEscopo).matches()) {
            return semEscopo;
        }
        if (semEscopo.length() > TAMANHO_MAXIMO_IP || !IPV6.matcher(semEscopo).matches()) {
            return null;
        }
        try {
            InetAddress.getByName(semEscopo);
            return semEscopo;
        } catch (UnknownHostException erro) {
            return null;
        }
    }

    /** Navegador sem caractere de controle e com no máximo 500 caracteres. */
    static String normalizarUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String limpo = CONTROLE.matcher(userAgent).replaceAll("");
        return limpo.length() <= TAMANHO_MAXIMO_USER_AGENT ? limpo : limpo.substring(0, TAMANHO_MAXIMO_USER_AGENT);
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
