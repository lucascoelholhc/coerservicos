package br.com.coe.servicos.avaliacao;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Média e total das avaliações do profissional (RN23, RN55): só as visíveis feitas por clientes. Toda mudança numa
 * avaliação (nova, oculta ou reexibida pelo admin) chama {@link #refazer} na mesma transação. A linha do profissional
 * fica travada com {@code FOR NO KEY UPDATE} (não bloqueia as FKs de contrato e diária) e o recálculo é uma instrução
 * nova depois da trava: duas mudanças ao mesmo tempo não perdem a conta.
 */
@Component
public class MediaDoProfissional {

    private final NamedParameterJdbcTemplate jdbc;

    MediaDoProfissional(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refazer(UUID perfil) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("perfil", perfil);
        jdbc.queryForList("SELECT id FROM profissional WHERE id = :perfil FOR NO KEY UPDATE", parametros, UUID.class);
        jdbc.update("""
                UPDATE profissional p
                   SET nota_media = m.media, total_avaliacoes = m.total
                  FROM (SELECT round(avg(a.nota), 2) AS media, count(*)::int AS total
                          FROM avaliacao a
                          JOIN profissional pf ON pf.usuario_id = a.avaliado_id
                         WHERE pf.id = :perfil AND a.papel_autor = 'cliente' AND a.visivel) m
                 WHERE p.id = :perfil
                """, parametros);
    }
}
