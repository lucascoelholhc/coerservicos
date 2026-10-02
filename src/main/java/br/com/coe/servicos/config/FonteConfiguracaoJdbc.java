package br.com.coe.servicos.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lê a view {@code configuracao_vigente}: o valor mais recente de cada chave com vigência já
 * iniciada. A vigência é comparada com o relógio do banco ({@code now()}), não com o Clock da aplicação.
 */
@Component
class FonteConfiguracaoJdbc implements FonteConfiguracao {

    private final JdbcTemplate jdbc;

    FonteConfiguracaoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> lerVigentes() {
        Map<String, String> valores = new HashMap<>();
        jdbc.query("SELECT chave, valor FROM configuracao_vigente", linha -> {
            valores.put(linha.getString("chave"), linha.getString("valor"));
        });
        return valores;
    }
}
