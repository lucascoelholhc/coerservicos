package br.com.coe.servicos.compartilhado.armazenamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta o adaptador do S3; configuração errada (ex.: endpoint próprio no prod) = a aplicação não sobe. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesDoArmazenamento.class)
class ConfiguracaoDoArmazenamento {

    @Bean(destroyMethod = "close")
    ArmazenamentoS3 armazenamentoS3(
            PropriedadesDoArmazenamento propriedades,
            @Value("${coe.seguranca.origens-somente-https:false}") boolean producao) {
        propriedades.validar(producao);
        ArmazenamentoS3 s3 = ArmazenamentoS3.criar(propriedades);
        if (propriedades.criarBucket()) {
            s3.garantirBucket();
        }
        return s3;
    }
}
