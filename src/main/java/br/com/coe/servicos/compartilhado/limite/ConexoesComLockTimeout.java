package br.com.coe.servicos.compartilhado.limite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Conexões do limitador: ao abrir a transação ({@code setAutoCommit(false)}, que o Bucket4j chama no
 * começo de cada cobrança) aplica {@code SET LOCAL lock_timeout}. Por ser LOCAL, o timeout termina com
 * a transação e a conexão volta ao pool sem ele (pedido do dia 11). O {@code setQueryTimeout} do
 * Bucket4j não serve: a resolução é em segundos.
 */
final class ConexoesComLockTimeout extends DelegatingDataSource {

    private final String comando;

    ConexoesComLockTimeout(DataSource pool, Duration lockTimeout) {
        super(pool);
        this.comando = "SET LOCAL lock_timeout = '" + lockTimeout.toMillis() + "ms'";
    }

    @Override
    public Connection getConnection() throws SQLException {
        return comTimeoutNaTransacao(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String usuario, String senha) throws SQLException {
        return comTimeoutNaTransacao(obtainTargetDataSource().getConnection(usuario, senha));
    }

    private Connection comTimeoutNaTransacao(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, metodo, argumentos) -> {
                    try {
                        Object resultado = metodo.invoke(real, argumentos);
                        if ("setAutoCommit".equals(metodo.getName()) && Boolean.FALSE.equals(argumentos[0])) {
                            try (Statement statement = real.createStatement()) {
                                statement.execute(comando);
                            }
                        }
                        return resultado;
                    } catch (InvocationTargetException erro) {
                        throw erro.getCause();
                    }
                });
    }
}
