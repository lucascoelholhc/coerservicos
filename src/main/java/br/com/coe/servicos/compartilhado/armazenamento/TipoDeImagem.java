package br.com.coe.servicos.compartilhado.armazenamento;

/** Formato em que a imagem é GRAVADA (a entrada aceita também WebP, que vira JPEG). */
public enum TipoDeImagem {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extensao;

    TipoDeImagem(String contentType, String extensao) {
        this.contentType = contentType;
        this.extensao = extensao;
    }

    public String contentType() {
        return contentType;
    }

    public String extensao() {
        return extensao;
    }

    /** O tipo pela extensão da chave ({@code .jpg} ou {@code .png}); outra extensão = chave inválida. */
    static TipoDeImagem daChave(String chave) {
        for (TipoDeImagem tipo : values()) {
            if (chave.endsWith("." + tipo.extensao)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Chave de arquivo inválida.");
    }
}
