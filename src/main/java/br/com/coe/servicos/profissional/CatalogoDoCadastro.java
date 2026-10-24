package br.com.coe.servicos.profissional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Só o que está ativo no catálogo (RN02, RN03): profissão, serviço e cidade pelo identificador público. */
@Component
class CatalogoDoCadastro {

    record Profissao(UUID id, String codigo, boolean exigeNr10) {}

    record Servico(UUID id, UUID profissaoId, String profissao, String nome) {}

    private static final RowMapper<Profissao> PROFISSAO = (linha, n) ->
            new Profissao(linha.getObject("id", UUID.class), linha.getString("codigo"), linha.getBoolean("exige_nr10"));
    private static final RowMapper<Servico> SERVICO = (linha, n) -> new Servico(
            linha.getObject("id", UUID.class),
            linha.getObject("profissao_id", UUID.class),
            linha.getString("codigo"),
            linha.getString("nome"));
    private static final String SELECT_SERVICO = "SELECT s.id, s.nome, p.id AS profissao_id, p.codigo FROM servico s"
            + " JOIN profissao p ON p.id = s.profissao_id";

    private final JdbcTemplate jdbc;

    CatalogoDoCadastro(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Profissao> profissaoAtiva(String codigo) {
        return jdbc
                .query("SELECT id, codigo, exige_nr10 FROM profissao WHERE codigo = ? AND ativa", PROFISSAO, codigo)
                .stream()
                .findFirst();
    }

    Optional<Profissao> profissao(UUID id) {
        return jdbc.query("SELECT id, codigo, exige_nr10 FROM profissao WHERE id = ?", PROFISSAO, id).stream()
                .findFirst();
    }

    /** Serviço ativo de uma das profissões permitidas, pela profissão e pelo nome (o público só vê o nome). */
    Optional<Servico> servicoAtivo(String profissao, String nome, Collection<UUID> profissoesPermitidas) {
        return jdbc
                .query(
                        SELECT_SERVICO + " WHERE p.codigo = ? AND s.nome = ? AND s.ativo AND p.ativa",
                        SERVICO,
                        profissao,
                        nome)
                .stream()
                .filter(servico -> profissoesPermitidas.contains(servico.profissaoId()))
                .findFirst();
    }

    /** Serviço ativo de uma das profissões permitidas, só pelo nome (foto do portfólio). */
    Optional<Servico> servicoAtivoPeloNome(String nome, Collection<UUID> profissoesPermitidas) {
        return jdbc
                .query(SELECT_SERVICO + " WHERE s.nome = ? AND s.ativo AND p.ativa ORDER BY p.ordem", SERVICO, nome)
                .stream()
                .filter(servico -> profissoesPermitidas.contains(servico.profissaoId()))
                .findFirst();
    }

    Optional<Servico> servico(UUID id) {
        return jdbc.query(SELECT_SERVICO + " WHERE s.id = ?", SERVICO, id).stream()
                .findFirst();
    }

    Optional<UUID> cidadeAtiva(int codigoIbge) {
        return jdbc
                .queryForList("SELECT id FROM cidade WHERE codigo_ibge = ? AND ativa", UUID.class, codigoIbge)
                .stream()
                .findFirst();
    }

    Optional<Integer> codigoIbge(UUID cidade) {
        return jdbc.queryForList("SELECT codigo_ibge FROM cidade WHERE id = ?", Integer.class, cidade).stream()
                .findFirst();
    }
}
