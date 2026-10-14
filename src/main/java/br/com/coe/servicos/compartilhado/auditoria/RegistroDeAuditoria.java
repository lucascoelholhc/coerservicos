package br.com.coe.servicos.compartilhado.auditoria;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.limite.ChaveDeIp;

/**
 * Grava em log_auditoria (só inserção), na transação de quem chama: a ação some junto se a operação
 * desfizer. Em {@code antes}/{@code depois} só dado não pessoal (ex.: {@code {"canal":"email"}}):
 * nunca CPF, Pix, endereço, celular, e-mail, senha, código ou token, nem mascarados. O IP é dado
 * pessoal: gravado agrupado como no rate limit (IPv6 por /64) e com prazo de guarda a definir (plano).
 * Data do Clock.
 */
@Component
public class RegistroDeAuditoria {

    /** Papel do ator nas ações feitas pela aplicação sozinha (job, bloqueio, reuso de sessão). */
    public static final String SISTEMA = "SISTEMA";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    RegistroDeAuditoria(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Ação de um ator. {@code ip} nulo = o da requisição atual (se houver).
     *
     * @param ator quem fez (nulo = sistema)
     * @param papel CLIENTE, PROFISSIONAL, ADMIN ou {@link #SISTEMA}
     */
    public void registrar(
            UUID ator,
            String papel,
            String acao,
            String entidade,
            UUID entidadeId,
            Map<String, ?> antes,
            Map<String, ?> depois,
            String ip) {
        jdbc.update(
                "INSERT INTO log_auditoria (ator_id, ator_papel, acao, entidade, entidade_id, antes, depois, ip,"
                        + " criado_em) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS inet), ?)",
                ator,
                papel,
                acao,
                entidade,
                entidadeId,
                json.writeValueAsString(antes),
                json.writeValueAsString(depois),
                ipAgrupado(ip),
                Timestamp.from(clock.instant()));
    }

    /** Ação da aplicação sozinha (sem ator). */
    public void registrarDoSistema(
            String acao, String entidade, UUID entidadeId, Map<String, ?> antes, Map<String, ?> depois, String ip) {
        registrar(null, SISTEMA, acao, entidade, entidadeId, antes, depois, ip);
    }

    private static String ipAgrupado(String ip) {
        String origem = ip;
        if (origem == null
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes requisicao) {
            origem = requisicao.getRequest().getRemoteAddr();
        }
        if (origem == null) {
            return null;
        }
        String chave = ChaveDeIp.de(origem);
        return ChaveDeIp.desconhecido(chave) ? null : chave;
    }
}
