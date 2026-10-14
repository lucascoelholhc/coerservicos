package br.com.coe.servicos.compartilhado.limite;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * O limitador usa um pool próprio e pequeno, com o mesmo login da aplicação (coe_app): numa inundação
 * ele espera no pool dele (até {@code espera-por-conexao}, depois 503) e nunca esgota o pool que o resto
 * da API usa (H1). O pool não é bean DataSource, para não desligar o DataSource automático do Spring.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesDeLimites.class)
@EnableScheduling // limpeza dos baldes vencidos (LimpezaDeBaldes)
class ConfiguracaoDoLimitador {

    @Bean
    LimitadorDeTaxa limitadorDeTaxa(JdbcConnectionDetails banco, PropriedadesDeLimites propriedades, Clock clock) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("limitador");
        config.setJdbcUrl(banco.getJdbcUrl());
        config.setUsername(banco.getUsername());
        config.setPassword(banco.getPassword());
        config.setMaximumPoolSize(propriedades.conexoes());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(propriedades.esperaPorConexao().toMillis());
        config.setInitializationFailTimeout(-1); // banco fora na subida: o limitador responde 503
        return new LimitadorDeTaxa(new HikariDataSource(config), propriedades, clock);
    }

    @Bean
    LimitadorEmMemoria limitadorEmMemoria(PropriedadesDeLimites propriedades, Clock clock) {
        return new LimitadorEmMemoria(propriedades, clock);
    }
}
