package br.com.coe.servicos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.coe.servicos.IntegracaoTest;

/** Sem N+1: o catálogo inteiro sai em no máximo uma consulta por tabela principal (três comandos). */
class FonteCatalogoJdbcTest extends IntegracaoTest {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("lê áreas e profissões, serviços e cidades em exatamente 3 comandos SQL")
    void tresComandos() {
        AtomicInteger comandos = new AtomicInteger();
        FonteCatalogoJdbc fonte = new FonteCatalogoJdbc(new JdbcTemplate(contando(dataSource, comandos)));

        CatalogoResposta catalogo = fonte.ler();

        assertThat(catalogo.areas()).isNotEmpty();
        assertThat(catalogo.cidades()).isNotEmpty();
        assertThat(comandos).hasValue(3);
    }

    /** DataSource que conta createStatement/prepareStatement/prepareCall de cada conexão. */
    private static DataSource contando(DataSource real, AtomicInteger comandos) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(real, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        return conexaoContando(conexao, comandos);
                    }
                    return resultado;
                });
    }

    private static Connection conexaoContando(Connection real, AtomicInteger comandos) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, metodo, args) -> {
                    String nome = metodo.getName();
                    if (nome.equals("createStatement")
                            || nome.equals("prepareStatement")
                            || nome.equals("prepareCall")) {
                        comandos.incrementAndGet();
                    }
                    return invocar(real, metodo, args);
                });
    }

    private static Object invocar(Object alvo, java.lang.reflect.Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException erro) {
            throw erro.getCause();
        }
    }
}
