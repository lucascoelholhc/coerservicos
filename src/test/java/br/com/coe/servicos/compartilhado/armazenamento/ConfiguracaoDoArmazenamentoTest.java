package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;

import br.com.coe.servicos.IntegracaoTest;

/** Os limites aprovados em 14/10 saem do application.yml, e o multipart cobre o maior deles. */
class ConfiguracaoDoArmazenamentoTest extends IntegracaoTest {

    @Autowired
    private ValidadorDeImagem validador;

    @Autowired
    private PropriedadesDeImagem imagem;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize multipartPorArquivo;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize multipartPorRequisicao;

    @Value("${coe.limites.corpo-maximo-upload}")
    private DataSize corpoDoUpload;

    @Test
    @DisplayName("8 MB por categoria, 40 MP, lado de 2560 px")
    void limitesAprovados() {
        for (Categoria categoria : Categoria.values()) {
            assertThat(imagem.limiteDe(categoria)).as(categoria.prefixo()).isEqualTo(DataSize.ofMegabytes(8));
        }
        assertThat(imagem.pixelsMaximo()).isEqualTo(40_000_000L);
        assertThat(imagem.ladoMaximo()).isEqualTo(2560);
        assertThat(imagem.processamentosSimultaneos()).isPositive();
        assertThat(validador).isNotNull();
        java.util.Map<Categoria, Float> esperadas = java.util.Map.of(
                Categoria.DOCUMENTO, 0.92f,
                Categoria.SELFIE, 0.92f,
                Categoria.EVIDENCIA, 0.92f,
                Categoria.DISPUTA, 0.92f,
                Categoria.PORTFOLIO, 0.85f,
                Categoria.PEDIDO, 0.85f);
        for (Categoria categoria : Categoria.values()) {
            assertThat(imagem.qualidadeDe(categoria)).as(categoria.prefixo()).isEqualTo(esperadas.get(categoria));
        }
    }

    @Test
    @DisplayName("o multipart aceita o maior arquivo de categoria, e o filtro de corpo aceita o multipart")
    void multipartCobreAsCategorias() {
        long maiorCategoria = 0;
        for (Categoria categoria : Categoria.values()) {
            maiorCategoria = Math.max(maiorCategoria, imagem.limiteDe(categoria).toBytes());
        }

        assertThat(multipartPorArquivo.toBytes()).isGreaterThanOrEqualTo(maiorCategoria);
        assertThat(multipartPorRequisicao.toBytes()).isGreaterThanOrEqualTo(multipartPorArquivo.toBytes());
        assertThat(corpoDoUpload).isEqualTo(multipartPorRequisicao);
    }
}
