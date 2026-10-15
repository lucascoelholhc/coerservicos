package br.com.coe.servicos.compartilhado.armazenamento;

import java.net.URI;
import java.time.Duration;

/**
 * Arquivos num bucket privado (RNF07, RNF14). Só recebe {@link ArquivoValidado}: a validação vem antes
 * ({@code ValidadorDeImagem}). A chave é gerada aqui ({@code <categoria>/<uuid>.<jpg|png>}); o nome
 * original do arquivo nunca é gravado. Leitura só por URL assinada e curta.
 */
public interface Armazenamento {

    /** Grava e devolve a chave nova (o que vai na coluna {@code chave_arquivo}). */
    String guardar(ArquivoValidado arquivo, Categoria categoria);

    /** URL de leitura assinada, válida por {@code validade} (até o máximo da configuração). */
    URI urlAssinada(String chave, Duration validade);

    /** Apaga o arquivo; apagar o que não existe não é erro. */
    void apagar(String chave);
}
