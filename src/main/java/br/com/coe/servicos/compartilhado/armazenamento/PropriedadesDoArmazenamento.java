package br.com.coe.servicos.compartilhado.armazenamento;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bucket dos arquivos ({@code coe.armazenamento.*}). Endpoint próprio e chaves só no local e no test
 * (SeaweedFS, path-style); em produção nenhum dos dois: S3 de verdade com o papel IAM da tarefa, e o
 * bucket é criado pela infraestrutura (AWS-04). Configuração errada = a aplicação não sobe.
 *
 * @param endpoint      S3 local ({@code http://localhost:8333}); vazio em produção
 * @param chaveDeAcesso {@code S3_ACCESS_KEY} do S3 local; vazia em produção
 * @param chaveSecreta  {@code S3_SECRET_KEY} do S3 local; vazia em produção
 * @param validadeUrl   validade padrão e máxima da URL assinada (até 15 min, RNF14)
 * @param kmsKeyId      chave KMS da criptografia no servidor (SSE-KMS); vazia = padrão do bucket
 * @param criarBucket   cria o bucket na subida se ele não existir (só local e test)
 */
@ConfigurationProperties("coe.armazenamento")
public record PropriedadesDoArmazenamento(
        String bucket,
        String endpoint,
        String regiao,
        String chaveDeAcesso,
        String chaveSecreta,
        Duration validadeUrl,
        String kmsKeyId,
        boolean criarBucket) {

    /** Teto da URL assinada: "validade curta" da RNF14. */
    static final Duration VALIDADE_MAXIMA = Duration.ofMinutes(15);

    /**
     * Confere a combinação; {@code producao} = perfil prod ({@code coe.seguranca.origens-somente-https}).
     *
     * @throws IllegalStateException com o motivo, sem nenhum segredo na mensagem
     */
    void validar(boolean producao) {
        if (vazio(bucket)) {
            throw invalida("coe.armazenamento.bucket é obrigatório (COE_S3_BUCKET)");
        }
        if (vazio(regiao)) {
            throw invalida("coe.armazenamento.regiao é obrigatória (COE_S3_REGIAO)");
        }
        if (validadeUrl == null
                || validadeUrl.isNegative()
                || validadeUrl.isZero()
                || validadeUrl.compareTo(VALIDADE_MAXIMA) > 0) {
            throw invalida("coe.armazenamento.validade-url precisa ficar entre 1 s e 15 min");
        }
        boolean temChave = !vazio(chaveDeAcesso) || !vazio(chaveSecreta);
        if (vazio(endpoint)) {
            if (temChave) {
                throw invalida("chaves do S3 só com endpoint próprio; no S3 da AWS vale o papel IAM");
            }
        } else {
            validarEndpoint(producao);
        }
        if (producao && criarBucket) {
            throw invalida("coe.armazenamento.criar-bucket não vale em produção (o bucket é da infraestrutura)");
        }
    }

    boolean usaEndpointProprio() {
        return !vazio(endpoint);
    }

    boolean usaKms() {
        return !vazio(kmsKeyId);
    }

    private void validarEndpoint(boolean producao) {
        if (producao) {
            throw invalida("coe.armazenamento.endpoint não vale em produção (S3 de verdade, sem endpoint próprio)");
        }
        if (vazio(chaveDeAcesso) || vazio(chaveSecreta)) {
            throw invalida("endpoint próprio precisa de S3_ACCESS_KEY e S3_SECRET_KEY (ver .env.example)");
        }
        try {
            URI uri = new URI(endpoint.strip());
            boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            boolean semCaminho = uri.getRawPath() == null || uri.getRawPath().isEmpty();
            if (!http || uri.getHost() == null || !semCaminho || uri.getRawQuery() != null) {
                throw invalida("coe.armazenamento.endpoint precisa ser http(s)://host:porta, sem caminho");
            }
        } catch (URISyntaxException erro) {
            throw invalida("coe.armazenamento.endpoint não é uma URL");
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Armazenamento mal configurado: " + motivo);
    }

    /** Nunca mostra a chave secreta. */
    @Override
    public String toString() {
        return "PropriedadesDoArmazenamento[bucket=" + bucket + ", endpoint=" + endpoint + ", regiao=" + regiao
                + ", chaveDeAcesso=" + chaveDeAcesso + ", chaveSecreta=***, validadeUrl=" + validadeUrl
                + ", kmsKeyId=" + kmsKeyId + ", criarBucket=" + criarBucket + "]";
    }
}
