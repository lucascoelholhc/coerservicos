package br.com.coe.servicos.profissional;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fila de cadastros em análise, mais antigo primeiro (RN11: meta de resposta em 24 h). Uma consulta só, com o
 * nome da conta e a profissão; usa o índice parcial {@code ix_profissional_fila_analise}.
 */
@Component
class FilaDeVerificacao {

    static final int POR_PAGINA = 50;
    /** Teto da página (OFFSET grande custa caro e a fila nunca chega perto disso). */
    static final int PAGINA_MAXIMA = 200;

    private static final String SQL = """
            SELECT p.id, u.nome, pr.codigo, p.enviado_analise_em, p.nr10_status = 'em_analise' AS nr10
              FROM profissional p
              JOIN usuario u ON u.id = p.usuario_id
              LEFT JOIN profissao pr ON pr.id = p.profissao_principal_id
             WHERE p.status = 'em_analise'
             ORDER BY p.enviado_analise_em, p.id
             LIMIT ? OFFSET ?""";

    private final JdbcTemplate jdbc;

    FilaDeVerificacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    VerificacaoResposta.Fila pagina(int pagina) {
        int numero = Math.clamp(pagina, 0, PAGINA_MAXIMA);
        // um a mais para saber se há outra página
        List<VerificacaoResposta.ItemDaFila> itens = jdbc.query(
                SQL,
                (linha, n) -> new VerificacaoResposta.ItemDaFila(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("codigo"),
                        linha.getTimestamp("enviado_analise_em").toInstant(),
                        linha.getBoolean("nr10")),
                POR_PAGINA + 1,
                (long) numero * POR_PAGINA);
        boolean temMais = itens.size() > POR_PAGINA;
        return new VerificacaoResposta.Fila(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }
}
