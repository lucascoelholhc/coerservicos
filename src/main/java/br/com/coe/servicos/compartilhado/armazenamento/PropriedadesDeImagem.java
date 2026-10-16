package br.com.coe.servicos.compartilhado.armazenamento;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Limites da validação de imagem ({@code coe.armazenamento.imagem.*}, CORE-09). Configuração errada = a
 * aplicação não sobe.
 *
 * @param tamanhoMaximo             bytes aceitos por categoria (toda categoria precisa ter o seu)
 * @param pixelsMaximo              largura x altura do cabeçalho; até 40 milhões (decodificação por
 *                                  subamostragem + vagas limitadas mantêm a memória sob controle)
 * @param ladoMaximo                lado maior da imagem gravada (reduzida, mantendo a proporção)
 * @param processamentosSimultaneos imagens decodificadas ao mesmo tempo nesta instância
 * @param esperaPorVaga             quanto esperar por uma vaga antes de responder 503
 * @param qualidadeJpeg             qualidade do JPEG gravado por categoria, de 0 a 1 (documento e selfie mais
 *                                  altas: o texto do RG/CNH precisa continuar legível)
 */
@ConfigurationProperties("coe.armazenamento.imagem")
public record PropriedadesDeImagem(
        Map<Categoria, DataSize> tamanhoMaximo,
        long pixelsMaximo,
        int ladoMaximo,
        int processamentosSimultaneos,
        Duration esperaPorVaga,
        Map<Categoria, Float> qualidadeJpeg) {

    /** Teto aprovado (14/10): acima disso nem a subamostragem segura a memória com folga. */
    static final long PIXELS_TETO = 40_000_000L;

    private static final int LADO_TETO = 8192;

    void validar() {
        for (Categoria categoria : Categoria.values()) {
            DataSize tamanho = tamanhoMaximo == null ? null : tamanhoMaximo.get(categoria);
            if (tamanho == null || tamanho.toBytes() <= 0) {
                throw invalida("tamanho-maximo." + categoria.prefixo() + " é obrigatório e positivo");
            }
        }
        if (pixelsMaximo <= 0 || pixelsMaximo > PIXELS_TETO) {
            throw invalida("pixels-maximo precisa ficar entre 1 e " + PIXELS_TETO);
        }
        if (ladoMaximo <= 0 || ladoMaximo > LADO_TETO) {
            throw invalida("lado-maximo precisa ficar entre 1 e " + LADO_TETO);
        }
        if (processamentosSimultaneos <= 0) {
            throw invalida("processamentos-simultaneos precisa ser positivo");
        }
        if (esperaPorVaga == null || esperaPorVaga.isNegative() || esperaPorVaga.isZero()) {
            throw invalida("espera-por-vaga precisa ser positiva");
        }
        for (Categoria categoria : Categoria.values()) {
            Float qualidade = qualidadeJpeg == null ? null : qualidadeJpeg.get(categoria);
            if (qualidade == null || !(qualidade > 0f && qualidade <= 1f)) {
                throw invalida("qualidade-jpeg." + categoria.prefixo() + " precisa ficar entre 0 e 1");
            }
        }
    }

    float qualidadeDe(Categoria categoria) {
        return qualidadeJpeg.get(categoria);
    }

    DataSize limiteDe(Categoria categoria) {
        return tamanhoMaximo.get(categoria);
    }

    private static IllegalStateException invalida(String motivo) {
        return new IllegalStateException("Validação de imagem mal configurada: coe.armazenamento.imagem." + motivo);
    }
}
