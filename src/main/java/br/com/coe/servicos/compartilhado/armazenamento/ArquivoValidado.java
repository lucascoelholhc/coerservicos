package br.com.coe.servicos.compartilhado.armazenamento;

import java.util.Arrays;

/**
 * Imagem que já passou pela validação (tipo real, tamanho, pixels) e foi gravada de novo sem
 * metadados. Só este pacote cria uma: o {@link Armazenamento} nunca recebe bytes crus do usuário.
 */
public final class ArquivoValidado {

    private final byte[] conteudo;
    private final TipoDeImagem tipo;

    ArquivoValidado(byte[] conteudo, TipoDeImagem tipo) {
        if (conteudo == null || conteudo.length == 0 || tipo == null) {
            throw new IllegalArgumentException("Arquivo vazio.");
        }
        this.conteudo = conteudo.clone();
        this.tipo = tipo;
    }

    public TipoDeImagem tipo() {
        return tipo;
    }

    public int tamanho() {
        return conteudo.length;
    }

    /** Cópia: quem recebe não altera o arquivo validado. */
    public byte[] conteudo() {
        return conteudo.clone();
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof ArquivoValidado arquivo
                && tipo == arquivo.tipo
                && Arrays.equals(conteudo, arquivo.conteudo);
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + Arrays.hashCode(conteudo);
    }

    /** Nunca o conteúdo no log. */
    @Override
    public String toString() {
        return "ArquivoValidado[tipo=" + tipo + ", bytes=" + conteudo.length + "]";
    }
}
