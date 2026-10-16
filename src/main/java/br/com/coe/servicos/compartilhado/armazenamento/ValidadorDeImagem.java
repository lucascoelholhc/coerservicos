package br.com.coe.servicos.compartilhado.armazenamento;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.util.unit.DataSize;

import br.com.coe.servicos.compartilhado.erro.ArquivoRecusadoException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;

/**
 * Validação de upload de imagem (RNF07), toda no servidor e antes de guardar:
 *
 * <ol>
 *   <li>tamanho da categoria (413) e tipo real pelos primeiros bytes, só JPEG, PNG e WebP (415);
 *   <li>pixels pelo cabeçalho, antes de decodificar (422), e uma vaga de processamento (503 se não houver);
 *   <li>decodificação com subamostragem quando a imagem passa muito do lado máximo (memória limitada);
 *   <li>cores em sRGB, lado maior reduzido, orientação do EXIF aplicada nos pixels;
 *   <li>gravada de novo sem nenhum metadado: JPEG (WebP e JPEG, fundo branco no lugar da transparência) ou
 *       PNG (PNG, com a transparência). O que vinha colado depois da imagem (poliglota) não passa.
 * </ol>
 *
 * HEIC e PDF não são aceitos (decisão de 14/10). O nome original do arquivo nunca chega aqui.
 */
public class ValidadorDeImagem {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final int POSICAO_WEBP = 8;
    private static final String OCUPADO = "Estamos processando muitas fotos agora. Tente de novo em instantes.";

    private final PropriedadesDeImagem propriedades;
    private final Semaphore vagas;

    public ValidadorDeImagem(PropriedadesDeImagem propriedades) {
        propriedades.validar();
        this.propriedades = propriedades;
        this.vagas = new Semaphore(propriedades.processamentosSimultaneos(), true);
    }

    /**
     * Valida e grava de novo a imagem enviada.
     *
     * @throws ArquivoRecusadoException tamanho (413), tipo (415) ou resolução (422)
     * @throws ServicoInstavelException sem vaga de processamento dentro da espera (503)
     */
    public ArquivoValidado validar(byte[] bruto, Categoria categoria) {
        if (bruto == null || bruto.length == 0) {
            throw ArquivoRecusadoException.tipoInvalido();
        }
        DataSize limite = propriedades.limiteDe(categoria);
        if (bruto.length > limite.toBytes()) {
            throw ArquivoRecusadoException.grandeDemais(formatar(limite));
        }
        Formato formato = detectar(bruto);
        ocuparVaga();
        try {
            return processar(bruto, formato);
        } finally {
            vagas.release();
        }
    }

    /** Só para os testes: ocupar as vagas de fora. */
    Semaphore vagas() {
        return vagas;
    }

    private void ocuparVaga() {
        try {
            if (!vagas.tryAcquire(propriedades.esperaPorVaga().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ServicoInstavelException(OCUPADO, null);
            }
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new ServicoInstavelException(OCUPADO, erro);
        }
    }

    private ArquivoValidado processar(byte[] bruto, Formato formato) {
        BufferedImage lida = decodificar(bruto, formato);
        boolean comTransparencia =
                formato == Formato.PNG && lida.getColorModel().hasAlpha();
        BufferedImage imagem = emSrgb(lida, comTransparencia);
        imagem = reduzir(imagem, comTransparencia);
        if (formato == Formato.JPEG) {
            imagem = OrientacaoExif.aplicar(imagem, OrientacaoExif.ler(bruto));
        }
        if (formato == Formato.PNG) {
            return new ArquivoValidado(gravarPng(imagem), TipoDeImagem.PNG);
        }
        return new ArquivoValidado(gravarJpeg(imagem), TipoDeImagem.JPEG);
    }

    private BufferedImage decodificar(byte[] bruto, Formato formato) {
        Iterator<ImageReader> leitores = ImageIO.getImageReadersByFormatName(formato.nome);
        if (!leitores.hasNext()) {
            throw new IllegalStateException("Sem leitor de imagem para " + formato.nome);
        }
        ImageReader leitor = leitores.next();
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bruto))) {
            leitor.setInput(entrada, true, true);
            int largura = leitor.getWidth(0);
            int altura = leitor.getHeight(0);
            if (largura <= 0 || altura <= 0) {
                throw ArquivoRecusadoException.tipoInvalido();
            }
            if ((long) largura * altura > propriedades.pixelsMaximo()) {
                throw ArquivoRecusadoException.imagemGrandeDemais();
            }
            ImageReadParam parametros = leitor.getDefaultReadParam();
            int passo = Math.max(1, Math.max(largura, altura) / propriedades.ladoMaximo());
            parametros.setSourceSubsampling(passo, passo, 0, 0);
            return leitor.read(0, parametros);
        } catch (ArquivoRecusadoException erro) {
            throw erro;
        } catch (IOException | RuntimeException erro) {
            // arquivo corrompido ou montado para enganar o leitor: para o usuário, não é uma foto válida
            throw ArquivoRecusadoException.tipoInvalido();
        } finally {
            leitor.dispose();
        }
    }

    /** Cópia em sRGB 8 bits (RGB, ou ARGB se for manter a transparência); converte ICC e cinza. */
    private static BufferedImage emSrgb(BufferedImage origem, boolean comTransparencia) {
        BufferedImage destino = new BufferedImage(
                origem.getWidth(),
                origem.getHeight(),
                comTransparencia ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ColorSpace espaco = origem.getColorModel().getColorSpace();
        if (espaco.isCS_sRGB() || origem.getColorModel().hasAlpha()) {
            // transparência: o fundo branco entra aqui (a conversão de ICC com alfa fica a cargo do leitor)
            Graphics2D g = destino.createGraphics();
            if (!comTransparencia) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, destino.getWidth(), destino.getHeight());
            }
            g.drawImage(origem, 0, 0, null);
            g.dispose();
            return destino;
        }
        return new ColorConvertOp(null).filter(origem, destino);
    }

    private BufferedImage reduzir(BufferedImage imagem, boolean comTransparencia) {
        int ladoMaior = Math.max(imagem.getWidth(), imagem.getHeight());
        if (ladoMaior <= propriedades.ladoMaximo()) {
            return imagem;
        }
        double escala = (double) propriedades.ladoMaximo() / ladoMaior;
        int largura = Math.max(1, (int) Math.round(imagem.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(imagem.getHeight() * escala));
        BufferedImage reduzida = new BufferedImage(
                largura, altura, comTransparencia ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = reduzida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(imagem, 0, 0, largura, altura, null);
        g.dispose();
        return reduzida;
    }

    /** JPEG sem metadados além do JFIF básico (nada de EXIF, ICC, XMP ou comentário). */
    private byte[] gravarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream saida = new MemoryCacheImageOutputStream(bytes)) {
            escritor.setOutput(saida);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(propriedades.qualidadeJpeg());
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } catch (IOException erro) {
            throw new IllegalStateException("Falha ao gravar o JPEG", erro);
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] gravarPng(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("png").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream saida = new MemoryCacheImageOutputStream(bytes)) {
            escritor.setOutput(saida);
            escritor.write(null, new IIOImage(imagem, null, null), null);
        } catch (IOException erro) {
            throw new IllegalStateException("Falha ao gravar o PNG", erro);
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }

    private static Formato detectar(byte[] bytes) {
        if (comecaCom(bytes, 0, JPEG)) {
            return Formato.JPEG;
        }
        if (comecaCom(bytes, 0, PNG)) {
            return Formato.PNG;
        }
        if (comecaCom(bytes, 0, RIFF) && comecaCom(bytes, POSICAO_WEBP, WEBP)) {
            return Formato.WEBP;
        }
        throw ArquivoRecusadoException.tipoInvalido();
    }

    private static boolean comecaCom(byte[] bytes, int posicao, byte[] assinatura) {
        if (bytes.length < posicao + assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if (bytes[posicao + i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    /** "8 MB", ou "512 KB" se não for um número inteiro de MB. */
    static String formatar(DataSize tamanho) {
        if (tamanho.toBytes() % DataSize.ofMegabytes(1).toBytes() == 0) {
            return tamanho.toMegabytes() + " MB";
        }
        return Math.max(1, tamanho.toKilobytes()) + " KB";
    }

    /** Formato de entrada, pelo nome do leitor do ImageIO (WebP pela TwelveMonkeys). */
    private enum Formato {
        JPEG("jpeg"),
        PNG("png"),
        WEBP("webp");

        private final String nome;

        Formato(String nome) {
            this.nome = nome;
        }
    }
}
