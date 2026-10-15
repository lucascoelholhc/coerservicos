package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Sem S3 de verdade: como o cliente é montado e como a chave é gerada. */
class ArmazenamentoS3ClienteTest {

    private static final Pattern CHAVE = Pattern.compile(
            "(documento|selfie|portfolio|pedido|evidencia|disputa)/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png)");

    @Test
    @DisplayName("produção (sem endpoint): cliente sem endpointOverride, S3 de verdade")
    void producaoSemEndpointOverride() {
        try (ArmazenamentoS3 s3 = ArmazenamentoS3.criar(PropriedadesDoArmazenamentoTest.producao(""))) {
            assertThat(s3.usaEndpointProprio()).isFalse();
        }
    }

    @Test
    @DisplayName("local/test (com endpoint): endpointOverride + path-style")
    void localComEndpointOverride() {
        try (ArmazenamentoS3 s3 = ArmazenamentoS3.criar(
                PropriedadesDoArmazenamentoTest.local("http://localhost:8333", "chave", "segredo"))) {
            assertThat(s3.usaEndpointProprio()).isTrue();
        }
    }

    @Test
    @DisplayName("chave = <categoria>/<uuid aleatório>.<extensão>, sem colisão em 10 mil")
    void chaveGerada() {
        Set<String> chaves = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            Categoria categoria = Categoria.values()[i % Categoria.values().length];
            String chave = ArmazenamentoS3.novaChave(categoria, i % 2 == 0 ? TipoDeImagem.JPEG : TipoDeImagem.PNG);
            assertThat(chave).matches(CHAVE).startsWith(categoria.prefixo() + "/");
            chaves.add(chave);
        }
        assertThat(chaves).hasSize(10_000);
    }

    @Test
    @DisplayName("chave fora do padrão é recusada (não assina nem apaga caminho arbitrário)")
    void chaveForaDoPadrao() {
        for (String ruim : new String[] {
            "../segredo.jpg",
            "documento/../x.jpg",
            "outra/123e4567-e89b-12d3-a456-426614174000.jpg",
            "documento/123e4567-e89b-12d3-a456-426614174000.exe",
            "documento/123E4567-E89B-12D3-A456-426614174000.jpg",
            "",
            "documento/123e4567-e89b-12d3-a456-426614174000.jpg\n"
        }) {
            assertThat(ArmazenamentoS3.chaveValida(ruim)).as(ruim).isFalse();
        }
        assertThat(ArmazenamentoS3.chaveValida(null)).isFalse();
        assertThat(ArmazenamentoS3.chaveValida("selfie/123e4567-e89b-12d3-a456-426614174000.png"))
                .isTrue();
    }
}
