package br.com.coe.servicos.compartilhado.armazenamento;

import static br.com.coe.servicos.compartilhado.armazenamento.ImagensDeTeste.contem;
import static br.com.coe.servicos.compartilhado.armazenamento.ImagensDeTeste.juntar;
import static br.com.coe.servicos.compartilhado.armazenamento.ImagensDeTeste.ler;
import static br.com.coe.servicos.compartilhado.armazenamento.ImagensDeTeste.segmentosDoJpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import br.com.coe.servicos.compartilhado.erro.ArquivoRecusadoException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;

/** RNF07: tipo real, tamanho, pixels, sem metadados (orientação aplicada antes), sRGB, lado até o máximo. */
class ValidadorDeImagemTest {

    private static final int LADO_MAXIMO = 2560;

    private final ValidadorDeImagem validador = new ValidadorDeImagem(propriedades(40_000_000L, 2));

    @Test
    @DisplayName("JPEG bom: gravado como JPEG, mesmas dimensões")
    void jpegBom() {
        ArquivoValidado arquivo = validador.validar(ImagensDeTeste.jpeg(120, 80), Categoria.PORTFOLIO);

        assertThat(arquivo.tipo()).isEqualTo(TipoDeImagem.JPEG);
        BufferedImage saida = ler(arquivo.conteudo());
        assertThat(saida.getWidth()).isEqualTo(120);
        assertThat(saida.getHeight()).isEqualTo(80);
    }

    @Test
    @DisplayName("PNG bom continua PNG; PNG com transparência mantém a transparência")
    void png() {
        ArquivoValidado opaco = validador.validar(ImagensDeTeste.png(30, 30), Categoria.PEDIDO);
        ArquivoValidado transparente = validador.validar(ImagensDeTeste.pngTransparente(30, 30), Categoria.PEDIDO);

        assertThat(opaco.tipo()).isEqualTo(TipoDeImagem.PNG);
        assertThat(transparente.tipo()).isEqualTo(TipoDeImagem.PNG);
        assertThat(ler(transparente.conteudo()).getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(ler(transparente.conteudo()).getRGB(25, 5), true).getAlpha())
                .isZero();
    }

    @Test
    @DisplayName("WebP vira JPEG; com transparência, o fundo fica branco")
    void webp() {
        ArquivoValidado opaca = validador.validar(ImagensDeTeste.recurso("opaca.webp"), Categoria.PORTFOLIO);
        ArquivoValidado transparente =
                validador.validar(ImagensDeTeste.recurso("transparente.webp"), Categoria.PORTFOLIO);

        assertThat(opaca.tipo()).isEqualTo(TipoDeImagem.JPEG);
        assertThat(transparente.tipo()).isEqualTo(TipoDeImagem.JPEG);
        BufferedImage saida = ler(transparente.conteudo());
        assertThat(saida.getWidth()).isEqualTo(40);
        assertThat(saida.getHeight()).isEqualTo(20);
        Color fundo = new Color(saida.getRGB(20, 5));
        assertThat(fundo.getRed()).isGreaterThan(240);
        assertThat(fundo.getGreen()).isGreaterThan(240);
        assertThat(fundo.getBlue()).isGreaterThan(240);
        Color vermelho = new Color(saida.getRGB(3, 3));
        assertThat(vermelho.getRed()).isGreaterThan(200);
        assertThat(vermelho.getGreen()).isLessThan(60);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"exe", "pdf", "html", "gif", "vazio", "lixo-com-cabecalho-jpeg", "riff-sem-webp"})
    @DisplayName("arquivo que não é JPEG, PNG nem WebP de verdade: 415 arquivo-tipo-invalido")
    void tipoInvalido(String caso) {
        byte[] bytes = switch (caso) {
            case "exe" -> juntar("MZ".getBytes(StandardCharsets.US_ASCII), new byte[200]);
            case "pdf" -> "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            case "html" -> "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
            case "gif" -> "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
            case "vazio" -> new byte[0];
            case "lixo-com-cabecalho-jpeg" ->
                juntar(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, new byte[300]);
            default -> "RIFF\u0010\u0000\u0000\u0000AVI LIST".getBytes(StandardCharsets.ISO_8859_1);
        };

        assertThatThrownBy(() -> validador.validar(bytes, Categoria.DOCUMENTO))
                .isInstanceOfSatisfying(ArquivoRecusadoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("arquivo-tipo-invalido");
                    assertThat(erro.getStatus()).isEqualTo(415);
                });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"exe", "html"})
    @DisplayName("o que nem é imagem: pede JPG ou PNG")
    void mensagemDeTipo(String caso) {
        byte[] bytes = caso.equals("exe")
                ? juntar("MZ".getBytes(StandardCharsets.US_ASCII), new byte[200])
                : "<html></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validador.validar(bytes, Categoria.DOCUMENTO)).hasMessageContaining("JPG ou PNG");
    }

    @Test
    @DisplayName("imagem que não abre (corrompida ou cortada): 415 com mensagem para tirar outra foto")
    void imagemQueNaoAbre() {
        byte[] inteiro = ImagensDeTeste.jpeg(200, 200);
        byte[] cortado = java.util.Arrays.copyOf(inteiro, inteiro.length * 6 / 10);

        assertThatThrownBy(() -> validador.validar(cortado, Categoria.DOCUMENTO))
                .isInstanceOfSatisfying(ArquivoRecusadoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("arquivo-tipo-invalido");
                    assertThat(erro.getStatus()).isEqualTo(415);
                    assertThat(erro.getMessage()).contains("Não conseguimos abrir");
                });
    }

    @Test
    @DisplayName("JPEG com perfil ICC quebrado: aceito sem o ICC ou 415, nunca 500")
    void iccQuebrado() {
        try {
            ArquivoValidado arquivo = validador.validar(ImagensDeTeste.jpegComIccQuebrado(), Categoria.DOCUMENTO);
            assertThat(segmentosDoJpeg(arquivo.conteudo())).doesNotContain(0xE2);
        } catch (ArquivoRecusadoException recusado) {
            assertThat(recusado.getStatus()).isEqualTo(415);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"zTXt", "iTXt", "iCCP", "eXIf"})
    @DisplayName("PNG com metadado grande (bomba de descompressão): recusado antes de abrir")
    void pngComMetadadoGrande(String tipo) {
        byte[] bomba = ImagensDeTeste.pngComChunk(tipo, 100 * 1024, 1);

        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> assertThatThrownBy(() -> validador.validar(bomba, Categoria.PORTFOLIO))
                        .isInstanceOfSatisfying(
                                ArquivoRecusadoException.class,
                                erro -> assertThat(erro.getCodigo()).isEqualTo("arquivo-tipo-invalido")));
    }

    @Test
    @DisplayName("PNG com milhares de chunks: recusado")
    void pngComChunksDemais() {
        byte[] muitos = ImagensDeTeste.pngComChunk("tEXt", 4, 5000);

        assertThatThrownBy(() -> validador.validar(muitos, Categoria.PORTFOLIO))
                .isInstanceOf(ArquivoRecusadoException.class);
    }

    @Test
    @DisplayName("PNG com metadado pequeno passa (e sai sem ele)")
    void pngComMetadadoPequeno() {
        ArquivoValidado arquivo = validador.validar(ImagensDeTeste.pngComChunk("tEXt", 100, 1), Categoria.PORTFOLIO);

        assertThat(contem(arquivo.conteudo(), "tEXt")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"EXIF", "ICCP", "XMP "})
    @DisplayName("WebP com metadado grande: recusado antes de abrir")
    void webpComMetadadoGrande(String tipo) {
        assertThatThrownBy(() -> validador.validar(ImagensDeTeste.webpComChunk(tipo, 100 * 1024), Categoria.PORTFOLIO))
                .isInstanceOf(ArquivoRecusadoException.class);
    }

    @Test
    @DisplayName("PNG transparente: a cor escondida embaixo do alfa zero é apagada")
    void corEscondidaApagada() {
        BufferedImage saida = ler(validador
                .validar(ImagensDeTeste.pngComCorEscondida(), Categoria.PORTFOLIO)
                .conteudo());

        assertThat(saida.getRGB(0, 0)).isZero();
        assertThat(saida.getRGB(1, 1)).isEqualTo(0xFF00FF00);
    }

    @Test
    @DisplayName("a vaga volta mesmo quando a imagem é recusada no meio")
    void vagaVoltaNaRecusa() {
        int antes = validador.vagas().availablePermits();

        byte[] inteiro = ImagensDeTeste.jpeg(200, 200);
        assertThatThrownBy(
                        () -> validador.validar(java.util.Arrays.copyOf(inteiro, inteiro.length / 2), Categoria.PEDIDO))
                .isInstanceOf(ArquivoRecusadoException.class);
        assertThatThrownBy(() -> validador.validar(ImagensDeTeste.pngQueDeclara(50_000, 50_000), Categoria.PEDIDO))
                .isInstanceOf(ArquivoRecusadoException.class);

        assertThat(validador.vagas().availablePermits()).isEqualTo(antes);
    }

    @Test
    @DisplayName("nulo: 415")
    void nulo() {
        assertThatThrownBy(() -> validador.validar(null, Categoria.DOCUMENTO))
                .isInstanceOf(ArquivoRecusadoException.class);
    }

    @Test
    @DisplayName("poliglota (JPEG + HTML/ZIP colado no fim): aceito, mas gravado de novo sem o que vinha colado")
    void poliglota() {
        byte[] colado = juntar(
                "<html><script>fetch('/api')</script></html>".getBytes(StandardCharsets.UTF_8),
                new byte[] {'P', 'K', 3, 4},
                "conteudo-do-zip".getBytes(StandardCharsets.UTF_8));
        byte[] poliglota = juntar(ImagensDeTeste.jpeg(50, 50), colado);

        ArquivoValidado arquivo = validador.validar(poliglota, Categoria.EVIDENCIA);

        assertThat(contem(arquivo.conteudo(), "<script>")).isFalse();
        assertThat(contem(arquivo.conteudo(), "conteudo-do-zip")).isFalse();
    }

    @Test
    @DisplayName("acima do tamanho da categoria: 413 arquivo-grande-demais, com o limite na mensagem")
    void grandeDemais() {
        byte[] grande = ImagensDeTeste.png(400, 400);

        assertThatThrownBy(() -> validador.validar(grande, Categoria.SELFIE))
                .isInstanceOfSatisfying(ArquivoRecusadoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("arquivo-grande-demais");
                    assertThat(erro.getStatus()).isEqualTo(413);
                    assertThat(erro.getMessage()).contains("1 KB");
                });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"png", "jpeg"})
    @DisplayName("cabeçalho declarando 50.000 x 50.000: 422 imagem-grande-demais, sem decodificar")
    void dimensoesGigantes(String formato) {
        byte[] bomba = formato.equals("png")
                ? ImagensDeTeste.pngQueDeclara(50_000, 50_000)
                : ImagensDeTeste.jpegQueDeclara(50_000, 50_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> assertThatThrownBy(() -> validador.validar(bomba, Categoria.PORTFOLIO))
                        .isInstanceOfSatisfying(ArquivoRecusadoException.class, erro -> {
                            assertThat(erro.getCodigo()).isEqualTo("imagem-grande-demais");
                            assertThat(erro.getStatus()).isEqualTo(422);
                        }));
    }

    @Test
    @DisplayName("acima dos pixels configurados: 422; no limite, aceita")
    void limiteDePixels() {
        ValidadorDeImagem apertado = new ValidadorDeImagem(propriedades(10_000L, 2));

        assertThat(apertado.validar(ImagensDeTeste.png(100, 100), Categoria.PORTFOLIO))
                .isNotNull();
        assertThatThrownBy(() -> apertado.validar(ImagensDeTeste.png(101, 100), Categoria.PORTFOLIO))
                .isInstanceOfSatisfying(
                        ArquivoRecusadoException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("imagem-grande-demais"));
    }

    @Test
    @DisplayName("lado maior acima de 2560 px: reduzido mantendo a proporção")
    void reduzLadoMaior() {
        BufferedImage saida = ler(validador
                .validar(ImagensDeTeste.jpeg(6000, 1500), Categoria.PORTFOLIO)
                .conteudo());

        assertThat(saida.getWidth()).isEqualTo(LADO_MAXIMO);
        assertThat(saida.getHeight()).isEqualTo(640);
    }

    @Test
    @DisplayName("JPEG com GPS no EXIF: gravado sem EXIF, sem ICC e sem o texto escondido")
    void semExif() {
        byte[] comExif = ImagensDeTeste.jpegComExif(64, 48, 1);
        assertThat(segmentosDoJpeg(comExif)).contains(0xE1);
        assertThat(contem(comExif, ImagensDeTeste.SEGREDO_NO_EXIF)).isTrue();

        byte[] gravado = validador.validar(comExif, Categoria.DOCUMENTO).conteudo();

        assertThat(segmentosDoJpeg(gravado)).doesNotContain(0xE1, 0xE2, 0xED, 0xFE);
        assertThat(contem(gravado, ImagensDeTeste.SEGREDO_NO_EXIF)).isFalse();
        assertThat(contem(gravado, "Exif")).isFalse();
    }

    @Test
    @DisplayName("EXIF orientation=6: a foto é girada nos pixels (largura e altura trocadas) antes de tirar o EXIF")
    void orientacao6() {
        byte[] deitada = ImagensDeTeste.jpegComExif(80, 40, 6);

        BufferedImage saida =
                ler(validador.validar(deitada, Categoria.DOCUMENTO).conteudo());

        assertThat(saida.getWidth()).isEqualTo(40);
        assertThat(saida.getHeight()).isEqualTo(80);
        // 90° no sentido horário: o vermelho do canto de cima à esquerda vai para o de cima à direita
        Color cimaDireita = new Color(saida.getRGB(36, 3));
        assertThat(cimaDireita.getRed()).isGreaterThan(200);
        assertThat(cimaDireita.getBlue()).isLessThan(60);
    }

    @ParameterizedTest(name = "orientation={0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 0, 9})
    @DisplayName("as 8 orientações (e valores fora da faixa, ignorados) dão as dimensões certas")
    void orientacoes(int orientacao) {
        BufferedImage saida = ler(validador
                .validar(ImagensDeTeste.jpegComExif(80, 40, orientacao), Categoria.PORTFOLIO)
                .conteudo());

        boolean troca = orientacao >= 5 && orientacao <= 8;
        assertThat(saida.getWidth()).isEqualTo(troca ? 40 : 80);
        assertThat(saida.getHeight()).isEqualTo(troca ? 80 : 40);
    }

    @Test
    @DisplayName("imagem fora do sRGB (cinza, ou com ICC próprio): gravada em sRGB")
    void srgb() {
        BufferedImage cinza = ler(validador
                .validar(ImagensDeTeste.pngCinza(20, 20), Categoria.PEDIDO)
                .conteudo());
        BufferedImage webpComIcc = ler(validador
                .validar(ImagensDeTeste.recurso("transparente.webp"), Categoria.PEDIDO)
                .conteudo());

        assertThat(cinza.getColorModel().getColorSpace().isCS_sRGB()).isTrue();
        assertThat(webpComIcc.getColorModel().getColorSpace().isCS_sRGB()).isTrue();
    }

    @Test
    @DisplayName("todas as vagas de processamento ocupadas: 503 depois da espera, sem processar")
    void semVaga() throws InterruptedException {
        ValidadorDeImagem umaVaga = new ValidadorDeImagem(propriedades(40_000_000L, 1));
        umaVaga.vagas().acquire();
        try {
            assertThatThrownBy(() -> umaVaga.validar(ImagensDeTeste.jpeg(10, 10), Categoria.PEDIDO))
                    .isInstanceOf(ServicoInstavelException.class);
        } finally {
            umaVaga.vagas().release();
        }
        assertThat(umaVaga.validar(ImagensDeTeste.jpeg(10, 10), Categoria.PEDIDO))
                .isNotNull();
    }

    @Test
    @DisplayName("configuração: toda categoria com tamanho, números positivos")
    void configuracao() {
        Map<Categoria, DataSize> semSelfie = tamanhos();
        semSelfie.remove(Categoria.SELFIE);

        assertThatThrownBy(
                        () -> new PropriedadesDeImagem(semSelfie, 1000L, LADO_MAXIMO, 1, Duration.ofSeconds(1), 0.85f)
                                .validar())
                .hasMessageContaining("selfie");
        assertThatThrownBy(() -> new PropriedadesDeImagem(tamanhos(), 0L, LADO_MAXIMO, 1, Duration.ofSeconds(1), 0.85f)
                        .validar())
                .hasMessageContaining("pixels-maximo");
        assertThatThrownBy(
                        () -> new PropriedadesDeImagem(tamanhos(), 1000L, LADO_MAXIMO, 0, Duration.ofSeconds(1), 0.85f)
                                .validar())
                .hasMessageContaining("processamentos-simultaneos");
        assertThatThrownBy(
                        () -> new PropriedadesDeImagem(tamanhos(), 1000L, LADO_MAXIMO, 1, Duration.ofSeconds(1), 1.5f)
                                .validar())
                .hasMessageContaining("qualidade-jpeg");
    }

    private static PropriedadesDeImagem propriedades(long pixelsMaximo, int simultaneos) {
        PropriedadesDeImagem propriedades = new PropriedadesDeImagem(
                tamanhos(), pixelsMaximo, LADO_MAXIMO, simultaneos, Duration.ofMillis(100), 0.85f);
        propriedades.validar();
        return propriedades;
    }

    private static Map<Categoria, DataSize> tamanhos() {
        Map<Categoria, DataSize> tamanhos = new EnumMap<>(Categoria.class);
        for (Categoria categoria : Categoria.values()) {
            tamanhos.put(categoria, DataSize.ofMegabytes(8));
        }
        tamanhos.put(Categoria.SELFIE, DataSize.ofKilobytes(1));
        return tamanhos;
    }
}
