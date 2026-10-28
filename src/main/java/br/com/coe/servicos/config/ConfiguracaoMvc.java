package br.com.coe.servicos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.coe.servicos.compartilhado.seguranca.ContatoPendente;

/**
 * Interceptores do MVC: bloqueio de quem está com contato pendente (RN61), papel ADMIN conferido no banco no painel
 * (DOM-11) e limite por usuário (CORE-07).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoMvc implements WebMvcConfigurer {

    private final ContatoPendente contatoPendente;
    private final br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa limitador;
    private final JdbcTemplate jdbc;

    ConfiguracaoMvc(
            ContatoPendente contatoPendente,
            br.com.coe.servicos.compartilhado.limite.LimitadorDeTaxa limitador,
            JdbcTemplate jdbc) {
        this.contatoPendente = contatoPendente;
        this.limitador = limitador;
        this.jdbc = jdbc;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new BloqueioDeContatoPendente(contatoPendente));
        registro.addInterceptor(new PapelAdminNoBanco(jdbc)).addPathPatterns("/api/admin/**");
        registro.addInterceptor(new LimitePorUsuario(limitador));
    }
}
