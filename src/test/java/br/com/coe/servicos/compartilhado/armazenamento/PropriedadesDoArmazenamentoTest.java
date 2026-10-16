package br.com.coe.servicos.compartilhado.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PropriedadesDoArmazenamentoTest {

    private static final String ENDPOINT = "http://localhost:8333";
    static final String KMS = "arn:aws:kms:sa-east-1:000000000000:key/teste";

    @Test
    @DisplayName("local/test: endpoint próprio com as duas chaves passa")
    void localComEndpointEChaves() {
        assertThatCode(() -> local(ENDPOINT, "chave", "segredo").validar(false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("produção: sem endpoint e sem chaves (papel IAM) passa")
    void producaoSemEndpoint() {
        assertThatCode(() -> producao(KMS).validar(true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("produção sem chave KMS não sobe (documento e selfie com criptografia verificável)")
    void producaoExigeKms() {
        assertThatThrownBy(() -> producao("").validar(true)).hasMessageContaining("kms-key-id");
        assertThatCode(() -> producao("").validar(false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("produção com endpoint próprio não sobe")
    void producaoComEndpointNaoSobe() {
        PropriedadesDoArmazenamento comEndpoint = new PropriedadesDoArmazenamento(
                "coe-arquivos", ENDPOINT, "sa-east-1", "chave", "segredo", Duration.ofMinutes(5), "", false);

        assertThatThrownBy(() -> comEndpoint.validar(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coe.armazenamento.endpoint");
    }

    @Test
    @DisplayName("produção não cria o bucket (é da infraestrutura, AWS-04)")
    void producaoNaoCriaBucket() {
        PropriedadesDoArmazenamento criando = new PropriedadesDoArmazenamento(
                "coe-arquivos", null, "sa-east-1", null, null, Duration.ofMinutes(5), KMS, true);

        assertThatThrownBy(() -> criando.validar(true)).hasMessageContaining("criar-bucket");
    }

    @Test
    @DisplayName("endpoint sem as chaves, ou chave sem endpoint, não sobe")
    void chavesSoComEndpoint() {
        assertThatThrownBy(() -> local(ENDPOINT, "", "segredo").validar(false)).hasMessageContaining("S3_ACCESS_KEY");
        assertThatThrownBy(() -> local(ENDPOINT, "chave", " ").validar(false)).hasMessageContaining("S3_ACCESS_KEY");
        assertThatThrownBy(() -> local(null, "chave", "segredo").validar(false)).hasMessageContaining("papel IAM");
    }

    @Test
    @DisplayName("bucket, região e validade da URL são obrigatórios; validade até 15 min")
    void obrigatorios() {
        assertThatThrownBy(() ->
                        semEndpoint(" ", "sa-east-1", Duration.ofMinutes(5)).validar(true))
                .hasMessageContaining("bucket");
        assertThatThrownBy(() -> semEndpoint("b", "", Duration.ofMinutes(5)).validar(true))
                .hasMessageContaining("regiao");
        assertThatThrownBy(() -> semEndpoint("b", "sa-east-1", Duration.ZERO).validar(true))
                .hasMessageContaining("validade-url");
        assertThatThrownBy(() -> semEndpoint("b", "sa-east-1", null).validar(true))
                .hasMessageContaining("validade-url");
        assertThatThrownBy(() ->
                        semEndpoint("b", "sa-east-1", Duration.ofMinutes(16)).validar(true))
                .hasMessageContaining("validade-url");
    }

    @Test
    @DisplayName("endpoint precisa ser http(s) sem caminho")
    void endpointMalFormado() {
        assertThatThrownBy(() -> local("localhost:8333", "chave", "segredo").validar(false))
                .hasMessageContaining("coe.armazenamento.endpoint");
        assertThatThrownBy(() -> local("http://localhost:8333/bucket", "chave", "segredo")
                        .validar(false))
                .hasMessageContaining("coe.armazenamento.endpoint");
    }

    @Test
    @DisplayName("toString nunca mostra a chave secreta")
    void toStringSemSegredo() {
        assertThat(local(ENDPOINT, "chave-publica", "segredo-123").toString()).doesNotContain("segredo-123");
    }

    @Test
    @DisplayName("application.yml e application-prod.yml não têm endpoint nem chaves (prod usa S3 de verdade)")
    void producaoNaoTemEndpointNemChaves() throws IOException {
        for (String arquivo : List.of("application.yml", "application-prod.yml")) {
            for (PropertySource<?> fonte :
                    new YamlPropertySourceLoader().load(arquivo, new ClassPathResource(arquivo))) {
                assertThat(fonte.getProperty("coe.armazenamento.endpoint"))
                        .as(arquivo)
                        .isNull();
                assertThat(fonte.getProperty("coe.armazenamento.chave-de-acesso"))
                        .as(arquivo)
                        .isNull();
                assertThat(fonte.getProperty("coe.armazenamento.chave-secreta"))
                        .as(arquivo)
                        .isNull();
                assertThat(fonte.getProperty("coe.armazenamento.criar-bucket"))
                        .as(arquivo)
                        .isNull();
            }
        }
    }

    @Test
    @DisplayName("produção = perfil prod OU origens só https (os dois sinais, nunca um só)")
    void producaoPeloPerfil() {
        org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");
        org.springframework.mock.env.MockEnvironment local = new org.springframework.mock.env.MockEnvironment();
        local.setActiveProfiles("local");

        assertThat(ConfiguracaoDoArmazenamento.ehProducao(prod, false)).isTrue();
        assertThat(ConfiguracaoDoArmazenamento.ehProducao(local, true)).isTrue();
        assertThat(ConfiguracaoDoArmazenamento.ehProducao(local, false)).isFalse();
    }

    static PropriedadesDoArmazenamento local(String endpoint, String chave, String segredo) {
        return new PropriedadesDoArmazenamento(
                "coe-arquivos-teste", endpoint, "us-east-1", chave, segredo, Duration.ofMinutes(5), "", true);
    }

    static PropriedadesDoArmazenamento producao(String kms) {
        return new PropriedadesDoArmazenamento(
                "coe-arquivos", null, "sa-east-1", null, null, Duration.ofMinutes(5), kms, false);
    }

    private static PropriedadesDoArmazenamento semEndpoint(String bucket, String regiao, Duration validade) {
        return new PropriedadesDoArmazenamento(bucket, null, regiao, null, null, validade, KMS, false);
    }
}
