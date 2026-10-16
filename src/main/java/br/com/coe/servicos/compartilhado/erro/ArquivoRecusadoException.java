package br.com.coe.servicos.compartilhado.erro;

/**
 * Upload recusado na validação (RNF07, CORE-09). Cada motivo tem o seu status, como o
 * {@code corpo-grande-demais} da CORE-07: tamanho = 413, tipo = 415, resolução = 422.
 */
public class ArquivoRecusadoException extends RegraDeNegocioException {

    private final int status;

    private ArquivoRecusadoException(int status, String codigo, String mensagem) {
        super(codigo, mensagem);
        this.status = status;
    }

    /** Não é JPEG, PNG nem WebP de verdade (pelos bytes, não pelo nome), ou a imagem não abre. */
    public static ArquivoRecusadoException tipoInvalido() {
        return new ArquivoRecusadoException(415, "arquivo-tipo-invalido", "Envie a foto em JPG ou PNG.");
    }

    /** Parece imagem, mas não abre: corrompida, cortada no meio ou montada para enganar o leitor. */
    public static ArquivoRecusadoException naoAbre() {
        return new ArquivoRecusadoException(
                415, "arquivo-tipo-invalido", "Não conseguimos abrir essa foto. Tire outra e envie de novo.");
    }

    /** Passou do tamanho da categoria; {@code limite} já formatado (ex.: "8 MB"). */
    public static ArquivoRecusadoException grandeDemais(String limite) {
        return new ArquivoRecusadoException(
                413, "arquivo-grande-demais", "A foto é grande demais. Envie uma foto de até " + limite + ".");
    }

    /** Pixels demais (conferidos no cabeçalho, antes de abrir a imagem). */
    public static ArquivoRecusadoException imagemGrandeDemais() {
        return new ArquivoRecusadoException(
                422, "imagem-grande-demais", "A foto tem resolução grande demais. Envie uma foto menor.");
    }

    public int getStatus() {
        return status;
    }
}
