package br.com.coe.servicos.compartilhado.armazenamento;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Monta imagens e arquivos falsos para os testes do validador (nada vem de fora do teste). */
final class ImagensDeTeste {

    /** Texto que vai dentro do EXIF; o arquivo gravado não pode ter este texto. */
    static final String SEGREDO_NO_EXIF = "GPS -26.919400,-49.066100 CASA-DO-CLIENTE";

    private ImagensDeTeste() {}

    /** Imagem com vermelho no canto de cima à esquerda e azul no de baixo à direita. */
    static BufferedImage desenho(int largura, int altura, int tipo) {
        BufferedImage imagem = new BufferedImage(largura, altura, tipo);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.RED);
        g.fillRect(0, 0, Math.max(1, largura / 4), Math.max(1, altura / 4));
        g.setColor(Color.BLUE);
        g.fillRect(largura - Math.max(1, largura / 4), altura - Math.max(1, altura / 4), largura, altura);
        g.dispose();
        return imagem;
    }

    static byte[] jpeg(int largura, int altura) {
        return codificar(desenho(largura, altura, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    static byte[] png(int largura, int altura) {
        return codificar(desenho(largura, altura, BufferedImage.TYPE_INT_RGB), "png");
    }

    static byte[] pngTransparente(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(new Color(255, 0, 0, 128));
        g.fillRect(0, 0, largura / 2, altura);
        g.dispose();
        return codificar(imagem, "png");
    }

    static byte[] pngCinza(int largura, int altura) {
        return codificar(desenho(largura, altura, BufferedImage.TYPE_BYTE_GRAY), "png");
    }

    static byte[] codificar(BufferedImage imagem, String formato) {
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagem, formato, saida)) {
                throw new IllegalStateException("sem escritor para " + formato);
            }
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    static BufferedImage ler(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    static byte[] recurso(String nome) {
        try (InputStream entrada = ImagensDeTeste.class.getResourceAsStream("/imagens/" + nome)) {
            return entrada.readAllBytes();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    /** JPEG com um segmento APP1/Exif (orientação, descrição com coordenadas e um IFD de GPS) logo depois do SOI. */
    static byte[] jpegComExif(int largura, int altura, int orientacao) {
        byte[] jpeg = jpeg(largura, altura);
        byte[] exif = exif(orientacao);
        ByteBuffer segmento = ByteBuffer.allocate(4 + exif.length);
        segmento.put((byte) 0xFF)
                .put((byte) 0xE1)
                .putShort((short) (exif.length + 2))
                .put(exif);
        return juntar(new byte[] {jpeg[0], jpeg[1]}, segmento.array(), Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    /** Payload do APP1: "Exif\0\0" + TIFF big-endian com IFD0 (descrição, orientação, ponteiro do GPS) e IFD de GPS. */
    private static byte[] exif(int orientacao) {
        byte[] descricao = (SEGREDO_NO_EXIF + "\0").getBytes(StandardCharsets.US_ASCII);
        int ifd0 = 8;
        int entradasIfd0 = 3;
        int gps = ifd0 + 2 + entradasIfd0 * 12 + 4;
        int textoDescricao = gps + 2 + 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(textoDescricao + descricao.length);
        tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(ifd0);
        tiff.putShort((short) entradasIfd0);
        tiff.putShort((short) 0x010E)
                .putShort((short) 2)
                .putInt(descricao.length)
                .putInt(textoDescricao);
        tiff.putShort((short) 0x0112)
                .putShort((short) 3)
                .putInt(1)
                .putShort((short) orientacao)
                .putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gps);
        tiff.putInt(0);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0001)
                .putShort((short) 2)
                .putInt(2)
                .put((byte) 'S')
                .put((byte) 0)
                .putShort((short) 0);
        tiff.putInt(0);
        tiff.put(descricao);
        return juntar("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.array());
    }

    /** PNG só com o cabeçalho (IHDR) dizendo {@code largura x altura} e um IDAT minúsculo: bomba de descompressão. */
    static byte[] pngQueDeclara(int largura, int altura) {
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(largura)
                .putInt(altura)
                .put((byte) 8)
                .put((byte) 2)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        return juntar(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'},
                chunk("IHDR", ihdr.array()),
                chunk("IDAT", new byte[] {0x78, (byte) 0x9C, 0x03, 0x00, 0x00, 0x00, 0x00, 0x01}),
                chunk("IEND", new byte[0]));
    }

    /** JPEG pequeno com as dimensões do SOF trocadas por {@code largura x altura}. */
    static byte[] jpegQueDeclara(int largura, int altura) {
        byte[] jpeg = jpeg(16, 16);
        for (int i = 2; i + 9 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && ((jpeg[i + 1] & 0xFF) == 0xC0 || (jpeg[i + 1] & 0xFF) == 0xC2)) {
                jpeg[i + 5] = (byte) (altura >> 8);
                jpeg[i + 6] = (byte) altura;
                jpeg[i + 7] = (byte) (largura >> 8);
                jpeg[i + 8] = (byte) largura;
                return jpeg;
            }
        }
        throw new IllegalStateException("JPEG sem SOF");
    }

    /** Marcadores dos segmentos antes da imagem (até o SOS), ex.: 0xE0 (APP0), 0xE1 (EXIF), 0xE2 (ICC). */
    static List<Integer> segmentosDoJpeg(byte[] jpeg) {
        List<Integer> marcadores = new ArrayList<>();
        int i = 2;
        while (i + 3 < jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marcador = jpeg[i + 1] & 0xFF;
            marcadores.add(marcador);
            if (marcador == 0xDA) {
                break;
            }
            int tamanho = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            i += 2 + tamanho;
        }
        return marcadores;
    }

    static boolean contem(byte[] onde, String texto) {
        return new String(onde, StandardCharsets.ISO_8859_1).contains(texto);
    }

    static byte[] juntar(byte[]... partes) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            saida.writeBytes(parte);
        }
        return saida.toByteArray();
    }

    private static byte[] chunk(String tipo, byte[] dados) {
        byte[] nome = tipo.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(nome);
        crc.update(dados);
        ByteBuffer chunk = ByteBuffer.allocate(12 + dados.length);
        chunk.putInt(dados.length).put(nome).put(dados).putInt((int) crc.getValue());
        return chunk.array();
    }
}
