package br.com.coe.servicos.compartilhado.armazenamento;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;

/**
 * Adaptador do {@link Armazenamento} para o S3 (AWS SDK v2). É a única classe que conhece o SDK
 * (ArchUnit). Com {@code coe.armazenamento.endpoint} (local e test, SeaweedFS) usa endpoint próprio,
 * path-style e chaves estáticas; sem ele, o S3 da AWS com o papel IAM. A validade da URL assinada é
 * conferida pelo próprio S3 a partir do instante da assinatura.
 */
final class ArmazenamentoS3 implements Armazenamento, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ArmazenamentoS3.class);

    /** Só chaves geradas aqui: nunca assina nem apaga caminho vindo de fora. */
    private static final Pattern CHAVE_VALIDA = Pattern.compile("("
            + java.util.Arrays.stream(Categoria.values())
                    .map(Categoria::prefixo)
                    .collect(Collectors.joining("|"))
            + ")/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.("
            + java.util.Arrays.stream(TipoDeImagem.values())
                    .map(TipoDeImagem::extensao)
                    .collect(Collectors.joining("|"))
            + ")");

    private static final Duration TEMPO_DE_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TEMPO_DE_LEITURA = Duration.ofSeconds(30);
    // guardar roda dentro da transação do banco (a conexão espera junto): chamada curta
    private static final Duration TEMPO_DA_CHAMADA = Duration.ofSeconds(30);
    private static final String INDISPONIVEL =
            "Não foi possível acessar os arquivos agora. Tente de novo em instantes.";

    private final PropriedadesDoArmazenamento propriedades;
    private final S3Client cliente;
    private final S3Presigner assinador;

    private ArmazenamentoS3(PropriedadesDoArmazenamento propriedades, S3Client cliente, S3Presigner assinador) {
        this.propriedades = propriedades;
        this.cliente = cliente;
        this.assinador = assinador;
    }

    static ArmazenamentoS3 criar(PropriedadesDoArmazenamento propriedades) {
        Region regiao = Region.of(propriedades.regiao().strip());
        S3ClientBuilder cliente = S3Client.builder()
                .region(regiao)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(TEMPO_DE_CONEXAO)
                        .socketTimeout(TEMPO_DE_LEITURA))
                .overrideConfiguration(configuracao -> configuracao.apiCallTimeout(TEMPO_DA_CHAMADA));
        S3Presigner.Builder assinador = S3Presigner.builder().region(regiao);
        if (propriedades.usaEndpointProprio()) {
            URI endpoint = URI.create(propriedades.endpoint().strip());
            StaticCredentialsProvider chaves = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(propriedades.chaveDeAcesso(), propriedades.chaveSecreta()));
            cliente.endpointOverride(endpoint).forcePathStyle(true).credentialsProvider(chaves);
            assinador
                    .endpointOverride(endpoint)
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .credentialsProvider(chaves);
        }
        return new ArmazenamentoS3(propriedades, cliente.build(), assinador.build());
    }

    @Override
    public String guardar(ArquivoValidado arquivo, Categoria categoria) {
        String chave = novaChave(categoria, arquivo.tipo());
        PutObjectRequest.Builder pedido = PutObjectRequest.builder()
                .bucket(propriedades.bucket())
                .key(chave)
                .contentType(arquivo.tipo().contentType())
                .contentLength((long) arquivo.tamanho());
        if (propriedades.usaKms()) {
            pedido.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(propriedades.kmsKeyId());
        }
        try {
            cliente.putObject(pedido.build(), RequestBody.fromBytes(arquivo.conteudo()));
        } catch (SdkException erro) {
            throw new ServicoInstavelException(INDISPONIVEL, erro);
        }
        // Só a chave e a categoria: nunca o conteúdo nem o nome original (CORE-11).
        LOG.info("Arquivo guardado: {}, {} bytes", paraLog(chave), arquivo.tamanho());
        return chave;
    }

    @Override
    public URI urlAssinada(String chave, Duration validade) {
        exigirChaveValida(chave);
        if (validade == null
                || validade.isNegative()
                || validade.isZero()
                || validade.compareTo(propriedades.validadeUrl()) > 0) {
            throw new IllegalArgumentException("Validade da URL fora do permitido.");
        }
        GetObjectRequest leitura = GetObjectRequest.builder()
                .bucket(propriedades.bucket())
                .key(chave)
                .responseContentType(TipoDeImagem.daChave(chave).contentType())
                .responseContentDisposition("inline")
                .build();
        try {
            return assinador
                    .presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(validade)
                            .getObjectRequest(leitura)
                            .build())
                    .url()
                    .toURI();
        } catch (SdkException | URISyntaxException erro) {
            throw new ServicoInstavelException(INDISPONIVEL, erro);
        }
    }

    @Override
    public void apagar(String chave) {
        exigirChaveValida(chave);
        try {
            cliente.deleteObject(pedido -> pedido.bucket(propriedades.bucket()).key(chave));
        } catch (SdkException erro) {
            throw new ServicoInstavelException(INDISPONIVEL, erro);
        }
        LOG.info("Arquivo apagado: {}", paraLog(chave));
    }

    /** Local e test: cria o bucket (privado, o padrão do S3) se ele ainda não existir. */
    void garantirBucket() {
        String bucket = propriedades.bucket();
        try {
            cliente.headBucket(pedido -> pedido.bucket(bucket));
        } catch (S3Exception erro) {
            if (erro.statusCode() != 404) {
                throw erro;
            }
            cliente.createBucket(pedido -> pedido.bucket(bucket));
            LOG.info("Bucket criado: {}", bucket);
        }
    }

    boolean usaEndpointProprio() {
        return cliente.serviceClientConfiguration().endpointOverride().isPresent();
    }

    /** {@code <categoria>/<uuid aleatório>.<jpg|png>}: o UUID v4 vem do SecureRandom, sem nada do usuário. */
    static String novaChave(Categoria categoria, TipoDeImagem tipo) {
        return categoria.prefixo() + "/" + UUID.randomUUID() + "." + tipo.extensao();
    }

    /**
     * A chave no log como "categoria X, arquivo uuid.ext": o UUID sozinho escapa da máscara de token
     * (MascaraDeLog só poupa um UUID inteiro), e a chave não é dado pessoal.
     */
    static String paraLog(String chave) {
        int barra = chave.indexOf('/');
        return "categoria " + chave.substring(0, barra) + ", arquivo " + chave.substring(barra + 1);
    }

    static boolean chaveValida(String chave) {
        return chave != null && CHAVE_VALIDA.matcher(chave).matches();
    }

    private static void exigirChaveValida(String chave) {
        if (!chaveValida(chave)) {
            throw new IllegalArgumentException("Chave de arquivo inválida.");
        }
    }

    @Override
    public void close() {
        assinador.close();
        cliente.close();
    }
}
