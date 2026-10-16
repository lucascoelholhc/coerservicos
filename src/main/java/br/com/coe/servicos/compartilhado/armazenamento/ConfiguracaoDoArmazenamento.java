package br.com.coe.servicos.compartilhado.armazenamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Monta o adaptador do S3; configuração errada (ex.: endpoint próprio no prod) = a aplicação não sobe. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PropriedadesDoArmazenamento.class, PropriedadesDeImagem.class})
class ConfiguracaoDoArmazenamento {

    @Bean(destroyMethod = "close")
    ArmazenamentoS3 armazenamentoS3(
            PropriedadesDoArmazenamento propriedades,
            Environment ambiente,
            @Value("${coe.seguranca.origens-somente-https:false}") boolean somenteHttps) {
        propriedades.validar(ehProducao(ambiente, somenteHttps));
        ArmazenamentoS3 s3 = ArmazenamentoS3.criar(propriedades);
        if (propriedades.criarBucket()) {
            s3.garantirBucket();
        }
        return s3;
    }

    /**
     * Produção pelo perfil {@code prod} ou pelas origens só https: um sinal errado sozinho (perfil esquecido
     * ou flag desligada) não desliga a proteção (revisão de segurança da CORE-09).
     */
    static boolean ehProducao(Environment ambiente, boolean somenteHttps) {
        return somenteHttps || ambiente.acceptsProfiles(Profiles.of("prod"));
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
