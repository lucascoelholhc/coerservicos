package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.TestcontainersConfiguration;

/**
 * O adaptador contra o SeaweedFS de verdade (a mesma imagem do docker-compose). O bucket não é limpo
 * entre os testes: cada um grava arquivos novos (chave aleatória) e só olha para eles.
 */
class ArmazenamentoS3Test extends IntegracaoTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private ArmazenamentoS3 s3;

    @Test
    @DisplayName("grava e a URL assinada baixa os mesmos bytes, com o Content-Type do tipo detectado")
    void gravaEBaixa() throws Exception {
        byte[] conteudo = conteudoUnico();

        String chave = s3.guardar(new ArquivoValidado(conteudo, TipoDeImagem.PNG), Categoria.PORTFOLIO);
        HttpResponse<byte[]> resposta = baixar(s3.urlAssinada(chave, Duration.ofMinutes(1)));

        assertThat(chave).startsWith("portfolio/").endsWith(".png");
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).isEqualTo(conteudo);
        assertThat(resposta.headers().firstValue("Content-Type")).hasValue("image/png");
    }

    @Test
    @DisplayName("duas gravações do mesmo arquivo geram chaves diferentes e nenhuma sobrescreve a outra")
    void semColisao() throws Exception {
        ArquivoValidado arquivo = new ArquivoValidado(conteudoUnico(), TipoDeImagem.JPEG);

        String primeira = s3.guardar(arquivo, Categoria.DOCUMENTO);
        String segunda = s3.guardar(arquivo, Categoria.DOCUMENTO);

        assertThat(primeira).isNotEqualTo(segunda);
        assertThat(baixar(s3.urlAssinada(primeira, Duration.ofMinutes(1))).statusCode())
                .isEqualTo(200);
        assertThat(baixar(s3.urlAssinada(segunda, Duration.ofMinutes(1))).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("bucket privado: sem assinatura, 403")
    void bucketPrivado() throws Exception {
        String chave = s3.guardar(new ArquivoValidado(conteudoUnico(), TipoDeImagem.JPEG), Categoria.SELFIE);
        URI assinada = s3.urlAssinada(chave, Duration.ofMinutes(1));
        URI semAssinatura = new URI(assinada.getScheme(), assinada.getAuthority(), assinada.getPath(), null, null);

        assertThat(baixar(semAssinatura).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("a URL assinada para de funcionar depois da validade")
    void urlExpira() throws Exception {
        String chave = s3.guardar(new ArquivoValidado(conteudoUnico(), TipoDeImagem.JPEG), Categoria.EVIDENCIA);
        URI url = s3.urlAssinada(chave, Duration.ofSeconds(2));
        assertThat(baixar(url).statusCode()).as("ainda válida").isEqualTo(200);

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> baixar(url).statusCode() == 403);
    }

    @Test
    @DisplayName("apagar remove o arquivo; apagar de novo não é erro")
    void apagar() throws Exception {
        String chave = s3.guardar(new ArquivoValidado(conteudoUnico(), TipoDeImagem.PNG), Categoria.DISPUTA);

        s3.apagar(chave);

        assertThat(baixar(s3.urlAssinada(chave, Duration.ofMinutes(1))).statusCode())
                .isEqualTo(404);
        assertThatCode(() -> s3.apagar(chave)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("chave fora do padrão ou validade acima do máximo: recusa antes de falar com o S3")
    void recusaEntradaRuim() {
        String boa = "pedido/" + UUID.randomUUID() + ".jpg";

        assertThatThrownBy(() -> s3.urlAssinada("../x.jpg", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s3.apagar("pedido/qualquer.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s3.urlAssinada(boa, Duration.ofMinutes(16)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s3.urlAssinada(boa, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o teste usa a mesma imagem do SeaweedFS que o docker-compose")
    void mesmaImagemDoCompose() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"), StandardCharsets.UTF_8);

        assertThat(compose)
                .containsPattern(
                        "image: " + TestcontainersConfiguration.IMAGEM_SEAWEEDFS.replace(".", "\\.") + "\\r?\\n");
        assertThat(TestcontainersConfiguration.IMAGEM_SEAWEEDFS).doesNotContain("latest");
    }

    private static byte[] conteudoUnico() {
        return ("arquivo " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    }

    private static HttpResponse<byte[]> baixar(URI url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
