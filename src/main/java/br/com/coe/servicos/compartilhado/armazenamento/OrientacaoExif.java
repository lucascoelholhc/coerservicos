package br.com.coe.servicos.compartilhado.armazenamento;

import java.awt.image.BufferedImage;

/**
 * Orientação do EXIF de um JPEG (tag 0x0112 do IFD0) e a rotação/espelhamento correspondente nos pixels.
 * A foto do celular costuma vir "deitada" com a orientação no EXIF; como o EXIF é descartado ao gravar de
 * novo, a orientação precisa ir para os pixels antes. Lê o mínimo, com todos os limites conferidos: dado
 * estranho = orientação normal (1), nunca exceção.
 */
final class OrientacaoExif {

    static final int NORMAL = 1;

    private static final int SOI_TAMANHO = 2;
    private static final int APP1 = 0xE1;
    private static final int SOS = 0xDA;
    private static final int EOI = 0xD9;
    private static final int TAG_ORIENTACAO = 0x0112;
    private static final int TIPO_SHORT = 3;
    private static final byte[] CABECALHO_EXIF = {'E', 'x', 'i', 'f', 0, 0};

    private OrientacaoExif() {}

    /** Orientação de 1 a 8; sem EXIF, sem a tag ou com qualquer valor estranho, 1. */
    static int ler(byte[] jpeg) {
        int i = SOI_TAMANHO;
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marcador = jpeg[i + 1] & 0xFF;
            if (marcador == SOS || marcador == EOI) {
                return NORMAL;
            }
            int tamanho = u16(jpeg, i + 2, true);
            if (tamanho < 2 || i + 2 + tamanho > jpeg.length) {
                return NORMAL;
            }
            if (marcador == APP1 && comecaCom(jpeg, i + 4, CABECALHO_EXIF)) {
                return daTiff(jpeg, i + 4 + CABECALHO_EXIF.length, i + 2 + tamanho);
            }
            i += 2 + tamanho;
        }
        return NORMAL;
    }

    /** Aplica a orientação: 2–4 espelham/giram 180°, 5–8 trocam largura e altura. */
    static BufferedImage aplicar(BufferedImage origem, int orientacao) {
        if (orientacao < 2 || orientacao > 8) {
            return origem;
        }
        int largura = origem.getWidth();
        int altura = origem.getHeight();
        boolean troca = orientacao >= 5;
        int larguraDestino = troca ? altura : largura;
        BufferedImage destino = new BufferedImage(larguraDestino, troca ? largura : altura, origem.getType());
        int[] pixels = origem.getRGB(0, 0, largura, altura, null, 0, largura);
        int[] girados = new int[pixels.length];
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                girados[destinoDe(orientacao, x, y, largura, altura)] = pixels[y * largura + x];
            }
        }
        destino.setRGB(0, 0, larguraDestino, destino.getHeight(), girados, 0, larguraDestino);
        return destino;
    }

    /** Índice no destino do pixel (x, y) da origem. */
    private static int destinoDe(int orientacao, int x, int y, int largura, int altura) {
        return switch (orientacao) {
            case 2 -> y * largura + (largura - 1 - x);
            case 3 -> (altura - 1 - y) * largura + (largura - 1 - x);
            case 4 -> (altura - 1 - y) * largura + x;
            case 5 -> x * altura + y;
            case 6 -> x * altura + (altura - 1 - y);
            case 7 -> (largura - 1 - x) * altura + (altura - 1 - y);
            default -> (largura - 1 - x) * altura + y;
        };
    }

    /** TIFF entre {@code inicio} e {@code fim}: ordem dos bytes, IFD0 e a entrada da orientação. */
    private static int daTiff(byte[] dados, int inicio, int fim) {
        if (inicio + 8 > fim) {
            return NORMAL;
        }
        boolean bigEndian;
        if (dados[inicio] == 'M' && dados[inicio + 1] == 'M') {
            bigEndian = true;
        } else if (dados[inicio] == 'I' && dados[inicio + 1] == 'I') {
            bigEndian = false;
        } else {
            return NORMAL;
        }
        long ifd0 = u32(dados, inicio + 4, bigEndian);
        if (u16(dados, inicio + 2, bigEndian) != 42 || ifd0 < 8 || inicio + ifd0 + 2 > fim) {
            return NORMAL;
        }
        int entradas = inicio + (int) ifd0;
        int quantidade = u16(dados, entradas, bigEndian);
        for (int n = 0; n < quantidade; n++) {
            int entrada = entradas + 2 + n * 12;
            if (entrada + 12 > fim) {
                return NORMAL;
            }
            if (u16(dados, entrada, bigEndian) == TAG_ORIENTACAO) {
                if (u16(dados, entrada + 2, bigEndian) != TIPO_SHORT) {
                    return NORMAL;
                }
                int valor = u16(dados, entrada + 8, bigEndian);
                return valor >= 1 && valor <= 8 ? valor : NORMAL;
            }
        }
        return NORMAL;
    }

    private static boolean comecaCom(byte[] dados, int posicao, byte[] prefixo) {
        if (posicao + prefixo.length > dados.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (dados[posicao + i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u16(byte[] dados, int posicao, boolean bigEndian) {
        int a = dados[posicao] & 0xFF;
        int b = dados[posicao + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private static long u32(byte[] dados, int posicao, boolean bigEndian) {
        long primeiro = u16(dados, posicao, bigEndian);
        long segundo = u16(dados, posicao + 2, bigEndian);
        return bigEndian ? (primeiro << 16) | segundo : (segundo << 16) | primeiro;
    }
}
