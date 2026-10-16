package br.com.coe.servicos.compartilhado.armazenamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Monta o adaptador do S3; configuração errada (ex.: endpoint próprio no prod) = a aplicação não sobe. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PropriedadesDoArmazenamento.class, PropriedadesDeImagem.class})
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

    /** O que o resto do código injeta: exige transação e apaga o arquivo se ela for desfeita. */
    @Bean(destroyMethod = "close")
    @Primary
    ArmazenamentoTransacional armazenamento(ArmazenamentoS3 s3) {
        return new ArmazenamentoTransacional(s3);
    }

    /** Valida os limites na subida (configuração errada = a aplicação não sobe). */
    @Bean
    ValidadorDeImagem validadorDeImagem(PropriedadesDeImagem propriedades) {
        return new ValidadorDeImagem(propriedades);
    }
}
