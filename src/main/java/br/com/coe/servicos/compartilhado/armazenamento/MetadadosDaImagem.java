package br.com.coe.servicos.compartilhado.armazenamento;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import br.com.coe.servicos.compartilhado.erro.ArquivoRecusadoException;

/**
 * Varredura dos blocos (chunks) do PNG e do WebP antes de abrir a imagem (revisões da CORE-09). Metadado
 * comprimido ({@code zTXt}, {@code iTXt}, {@code iCCP} no PNG; {@code ICCP}, {@code EXIF}, {@code XMP } no
 * WebP) é descompactado pelo leitor sem teto: 8 MB podem virar gigabytes antes do limite de pixels agir.
 * Aqui só os blocos de imagem podem ser grandes; qualquer outro acima de 64 KB, ou blocos demais, e a
 * imagem é recusada. Estrutura quebrada fica para o leitor, que recusa do mesmo jeito.
 */
final class MetadadosDaImagem {

    static final int BLOCO_MAXIMO = 64 * 1024;
    static final int BLOCOS_MAXIMOS = 256;

    private static final int ASSINATURA_PNG = 8;
    private static final int CABECALHO_RIFF = 12;
    private static final Set<String> DADOS_PNG = Set.of("IDAT");
    private static final Set<String> DADOS_WEBP = Set.of("VP8 ", "VP8L", "ALPH", "ANMF");

    private MetadadosDaImagem() {}

    /** PNG: comprimento (4, big-endian) + tipo (4) + dados + CRC (4), até o IEND. */
    static void conferirPng(byte[] png) {
        int posicao = ASSINATURA_PNG;
        int outros = 0;
        while (posicao + 8 <= png.length) {
            long tamanho = ((long) (png[posicao] & 0xFF) << 24)
                    | ((png[posicao + 1] & 0xFF) << 16)
                    | ((png[posicao + 2] & 0xFF) << 8)
                    | (png[posicao + 3] & 0xFF);
            String tipo = new String(png, posicao + 4, 4, StandardCharsets.ISO_8859_1);
            if (!DADOS_PNG.contains(tipo)) {
                exigirDentroDoLimite(tamanho, ++outros);
            }
            if ("IEND".equals(tipo)) {
                return;
            }
            posicao += 12 + (int) Math.min(tamanho, png.length);
        }
    }

    /** WebP: "RIFF" + tamanho + "WEBP", depois blocos FourCC (4) + tamanho (4, little-endian) + dados (par). */
    static void conferirWebp(byte[] webp) {
        int posicao = CABECALHO_RIFF;
        int outros = 0;
        while (posicao + 8 <= webp.length) {
            String tipo = new String(webp, posicao, 4, StandardCharsets.ISO_8859_1);
            long tamanho = (webp[posicao + 4] & 0xFFL)
                    | ((webp[posicao + 5] & 0xFFL) << 8)
                    | ((webp[posicao + 6] & 0xFFL) << 16)
                    | ((webp[posicao + 7] & 0xFFL) << 24);
            if (!DADOS_WEBP.contains(tipo)) {
                exigirDentroDoLimite(tamanho, ++outros);
            }
            posicao += 8 + (int) Math.min(tamanho + (tamanho & 1), webp.length);
        }
    }

    private static void exigirDentroDoLimite(long tamanho, int blocos) {
        if (tamanho > BLOCO_MAXIMO || blocos > BLOCOS_MAXIMOS) {
            throw ArquivoRecusadoException.naoAbre();
        }
    }
}
