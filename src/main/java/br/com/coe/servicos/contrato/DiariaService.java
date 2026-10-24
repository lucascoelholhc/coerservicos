package br.com.coe.servicos.contrato;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.armazenamento.Armazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.ArquivoValidado;
import br.com.coe.servicos.compartilhado.armazenamento.Categoria;
import br.com.coe.servicos.compartilhado.armazenamento.ValidadorDeImagem;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Execução da diária pelo profissional (DOM-08): "Cheguei" no dia (RN35), fotos do serviço e "Terminei o dia" com ao
 * menos uma foto (RN36), que deixa a diária aguardando o cliente com a liberação automática marcada (RN38, PA08).
 * Toda troca de status segue o {@link StatusDiaria} e grava o histórico. Travas: contrato, depois a diária (a mesma
 * ordem do pagamento e da expiração). Só o profissional da diária mexe nela; para os outros, 404.
 */
@Service
class DiariaService {

    private static final String NAO_ENCONTRADA = "Diária não encontrada.";
    private static final int FOTOS_POR_DIA = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final UsuarioAutenticado usuario;
    private final ConfiguracaoNegocio configuracao;
    private final ValidadorDeImagem validador;
    private final Armazenamento armazenamento;
    private final LiberacaoDeDiaria liberacao;
    private final Clock clock;

    DiariaService(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transacao,
            UsuarioAutenticado usuario,
            ConfiguracaoNegocio configuracao,
            ValidadorDeImagem validador,
            Armazenamento armazenamento,
            LiberacaoDeDiaria liberacao,
            Clock clock) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.usuario = usuario;
        this.configuracao = configuracao;
        this.validador = validador;
        this.armazenamento = armazenamento;
        this.liberacao = liberacao;
        this.clock = clock;
    }

    private record Travada(UUID id, UUID contrato, StatusDiaria status, LocalDate data) {}

    /** O cliente aprova o dia (RN37): só de aguardando; contestada fica com a equipe; não se desfaz. */
    DiariaDtos.Diaria aprovar(UUID id) {
        return transacao.execute(t -> {
            Travada diaria = travar(id, "SELECT contrato_id FROM diaria WHERE id = :id AND cliente_id = :eu");
            if (diaria.status() == StatusDiaria.CONTESTADA) {
                throw new RegraDeNegocioException(
                        "diaria-contestada", "Essa diária está com a equipe COE por causa da sua reclamação.");
            }
            diaria.status().exigirIrPara(StatusDiaria.LIBERADA);
            liberacao.liberar(diaria.contrato(), id, LiberacaoDeDiaria.Por.CLIENTE, usuario.id(), agora());
            return detalhe(id);
        });
    }

    DiariaDtos.Diaria cheguei(UUID id) {
        return transacao.execute(t -> {
            Travada diaria = travarDoProfissional(id);
            diaria.status().exigirIrPara(StatusDiaria.ANDAMENTO);
            if (!diaria.data().equals(FusoDeNegocio.hoje(clock))) {
                throw new RegraDeNegocioException("fora-do-dia", "Só dá para marcar \"Cheguei\" no dia do serviço.");
            }
            OffsetDateTime agora = agora();
            jdbc.update(
                    "UPDATE diaria SET status = 'andamento', chegou_em = :agora WHERE id = :id",
                    new MapSqlParameterSource("id", id).addValue("agora", agora));
            historico(id, diaria.status(), StatusDiaria.ANDAMENTO, agora);
            return detalhe(id);
        });
    }

    /** Uma foto do serviço por vez (o pedido tem até 11 MB), com a diária em andamento; até 5 por dia. */
    DiariaDtos.FotoEnviada foto(UUID id, byte[] bytes) {
        // conferência barata antes de decodificar a imagem (até 40 MP): dono e diária em andamento
        List<String> status = jdbc.queryForList(
                "SELECT d.status FROM diaria d JOIN profissional p ON p.id = d.profissional_id"
                        + " WHERE d.id = :id AND p.usuario_id = :eu",
                new MapSqlParameterSource("id", id).addValue("eu", usuario.id()),
                String.class);
        if (status.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        exigirAndamento(StatusDiaria.doCodigo(status.get(0)));
        ArquivoValidado arquivo = validador.validar(bytes, Categoria.EVIDENCIA);
        return transacao.execute(t -> {
            Travada diaria = travarDoProfissional(id);
            exigirAndamento(diaria.status()); // de novo, dentro da trava
            if (fotos(id) >= FOTOS_POR_DIA) {
                throw new RegraDeNegocioException(
                        "limite-de-fotos", "Cada dia aceita até " + FOTOS_POR_DIA + " fotos do serviço.");
            }
            UUID foto = UUID.randomUUID();
            String chave = armazenamento.guardar(arquivo, Categoria.EVIDENCIA);
            jdbc.update(
                    """
                    INSERT INTO evidencia_diaria (id, diaria_id, tipo, chave_arquivo, criado_em)
                    VALUES (:id, :diaria, 'fim_do_dia', :chave, :agora)
                    """,
                    new MapSqlParameterSource("id", foto)
                            .addValue("diaria", id)
                            .addValue("chave", chave)
                            .addValue("agora", agora()));
            return new DiariaDtos.FotoEnviada(foto);
        });
    }

    DiariaDtos.Diaria terminei(UUID id, String recado) {
        String texto = recado == null || recado.isBlank() ? null : recado.strip();
        return transacao.execute(t -> {
            Travada diaria = travarDoProfissional(id);
            diaria.status().exigirIrPara(StatusDiaria.AGUARDANDO);
            if (fotos(id) == 0) {
                throw new RegraDeNegocioException(
                        "foto-obrigatoria", "Mande pelo menos uma foto do serviço antes de terminar o dia.");
            }
            if (texto != null) {
                CensuraDeContato.exigirSemContato(texto, "recado");
                jdbc.update("""
                        UPDATE evidencia_diaria SET observacao = :recado
                         WHERE id = (SELECT id FROM evidencia_diaria WHERE diaria_id = :id AND tipo = 'fim_do_dia'
                                      ORDER BY criado_em DESC, id DESC LIMIT 1)
                        """, new MapSqlParameterSource("id", id).addValue("recado", texto));
            }
            OffsetDateTime agora = agora();
            jdbc.update(
                    """
                    UPDATE diaria SET status = 'aguardando', terminou_em = :agora, auto_libera_em = :libera
                     WHERE id = :id
                    """,
                    new MapSqlParameterSource("id", id)
                            .addValue("agora", agora)
                            .addValue("libera", agora.plus(configuracao.autoLiberacao())));
            historico(id, diaria.status(), StatusDiaria.AGUARDANDO, agora);
            return detalhe(id);
        });
    }

    /** A diária do profissional logado, com o contrato e a diária travados nessa ordem; de outro, 404. */
    private Travada travarDoProfissional(UUID id) {
        return travar(
                id,
                "SELECT d.contrato_id FROM diaria d JOIN profissional p ON p.id = d.profissional_id"
                        + " WHERE d.id = :id AND p.usuario_id = :eu");
    }

    /** Trava o contrato e depois a diária (a ordem de todo fluxo); {@code donoDaDiaria} filtra pelo logado. */
    private Travada travar(UUID id, String donoDaDiaria) {
        List<UUID> contratos = jdbc.queryForList(
                donoDaDiaria, new MapSqlParameterSource("id", id).addValue("eu", usuario.id()), UUID.class);
        if (contratos.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        jdbc.queryForList(
                "SELECT id FROM contrato WHERE id = :contrato FOR UPDATE",
                new MapSqlParameterSource("contrato", contratos.get(0)),
                UUID.class);
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT status, data FROM diaria WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id));
        return new Travada(
                id,
                contratos.get(0),
                StatusDiaria.doCodigo((String) linha.get("status")),
                ((java.sql.Date) linha.get("data")).toLocalDate());
    }

    private static void exigirAndamento(StatusDiaria status) {
        if (status != StatusDiaria.ANDAMENTO) {
            throw new RegraDeNegocioException(
                    "foto-fora-do-andamento",
                    "As fotos do serviço vão depois do \"Cheguei\" e antes de terminar o dia.");
        }
    }

    private int fotos(UUID diaria) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM evidencia_diaria WHERE diaria_id = :id AND tipo = 'fim_do_dia'",
                new MapSqlParameterSource("id", diaria),
                Integer.class);
        return total == null ? 0 : total;
    }

    private void historico(UUID diaria, StatusDiaria de, StatusDiaria para, OffsetDateTime agora) {
        jdbc.update(
                """
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, ator_id, criado_em)
                VALUES (:diaria, :de, :para, 'profissional', :ator, :agora)
                """,
                new MapSqlParameterSource("diaria", diaria)
                        .addValue("de", de.codigo())
                        .addValue("para", para.codigo())
                        .addValue("ator", usuario.id())
                        .addValue("agora", agora));
    }

    private DiariaDtos.Diaria detalhe(UUID id) {
        return jdbc.queryForObject(
                """
                SELECT id, contrato_id, data, tipo, status, chegou_em, terminou_em, auto_libera_em, liberada_em,
                       liberada_por
                  FROM diaria WHERE id = :id
                """,
                new MapSqlParameterSource("id", id),
                (linha, n) -> new DiariaDtos.Diaria(
                        linha.getObject("id", UUID.class),
                        linha.getObject("contrato_id", UUID.class),
                        linha.getObject("data", LocalDate.class),
                        linha.getString("tipo"),
                        linha.getString("status"),
                        instante(linha.getTimestamp("chegou_em")),
                        instante(linha.getTimestamp("terminou_em")),
                        instante(linha.getTimestamp("auto_libera_em")),
                        instante(linha.getTimestamp("liberada_em")),
                        linha.getString("liberada_por")));
    }

    private static Instant instante(Timestamp momento) {
        return momento == null ? null : momento.toInstant();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
