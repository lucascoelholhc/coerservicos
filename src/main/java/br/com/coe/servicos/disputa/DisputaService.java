package br.com.coe.servicos.disputa;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.Percentual;
import br.com.coe.servicos.compartilhado.dinheiro.TaxaPagaPor;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.contrato.LiberacaoDeDiaria;
import br.com.coe.servicos.contrato.ReembolsoDeDiaria;
import br.com.coe.servicos.contrato.StatusDiaria;

/**
 * Reclamação de uma diária (DOM-09; RN41–RN44e). Só o dia reclamado trava; o admin decide liberar (pela liberação de
 * sempre) ou reembolsar (exatamente o que o cliente pagou). Falta confirmada vira {@code ocorrencia_profissional} e o
 * cliente escolhe manter ou cancelar as outras diárias pagas. Ordem das travas: contrato → diária → disputa.
 */
@Service
class DisputaService {

    private static final String NAO_ENCONTRADA = "Reclamação não encontrada.";
    private static final int POR_PAGINA = 50;
    private static final List<String> EM_ABERTO = List.of("aberta", "em_analise");

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final ConfiguracaoNegocio configuracao;
    private final LiberacaoDeDiaria liberacao;
    private final ReembolsoDeDiaria reembolso;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    DisputaService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioAutenticado usuario,
            ConfiguracaoNegocio configuracao,
            LiberacaoDeDiaria liberacao,
            ReembolsoDeDiaria reembolso,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.configuracao = configuracao;
        this.liberacao = liberacao;
        this.reembolso = reembolso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** RN41/RN43: o cliente da diária reclama; só ela vai para {@code contestada}. */
    @Transactional
    DisputaDtos.Disputa reclamar(DisputaDtos.NovaReclamacao pedido) {
        UUID eu = usuario.id();
        List<UUID> contratos = jdbc.queryForList(
                "SELECT contrato_id FROM diaria WHERE id = :id AND cliente_id = :eu",
                new MapSqlParameterSource("id", pedido.diariaId()).addValue("eu", eu),
                UUID.class);
        if (contratos.isEmpty()) {
            throw new RecursoNaoEncontradoException("Diária não encontrada.");
        }
        StatusDiaria status = travar(contratos.get(0), pedido.diariaId());
        status.exigirIrPara(StatusDiaria.CONTESTADA);
        OffsetDateTime agora = agora();
        jdbc.update(
                "UPDATE diaria SET status = 'contestada' WHERE id = :id AND status = :de",
                new MapSqlParameterSource("id", pedido.diariaId()).addValue("de", status.codigo()));
        jdbc.update(
                """
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, ator_id, criado_em)
                VALUES (:diaria, :de, 'contestada', 'cliente', :eu, :agora)
                """,
                new MapSqlParameterSource("diaria", pedido.diariaId())
                        .addValue("de", status.codigo())
                        .addValue("eu", eu)
                        .addValue("agora", agora));
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO disputa (diaria_id, aberta_por, motivo, descricao, prazo_decisao_em, criado_em)
                VALUES (:diaria, :eu, :motivo, :descricao, :prazo, :agora)
                RETURNING id
                """,
                new MapSqlParameterSource("diaria", pedido.diariaId())
                        .addValue("eu", eu)
                        .addValue("motivo", pedido.motivo())
                        .addValue("descricao", pedido.descricao().strip())
                        .addValue("prazo", agora.plus(configuracao.prazoDisputa()))
                        .addValue("agora", agora),
                UUID.class);
        auditoria.registrar(
                eu,
                "CLIENTE",
                "disputa.abrir",
                "disputa",
                id,
                Map.of("status", status.codigo()),
                Map.of("motivo", pedido.motivo()),
                null);
        return detalhe(id);
    }

    @Transactional(readOnly = true)
    DisputaDtos.Disputa ver(UUID id) {
        daDisputa(id, "AND (d.cliente_id = :eu OR p.usuario_id = :eu)");
        return detalhe(id);
    }

    /** RN44: o profissional da diária responde uma vez, antes da decisão. */
    @Transactional
    DisputaDtos.Disputa responder(UUID id, DisputaDtos.Resposta pedido) {
        UUID eu = usuario.id();
        Map<String, Object> d = daDisputa(id, "AND p.usuario_id = :eu");
        travar((UUID) d.get("contrato_id"), (UUID) d.get("diaria_id"));
        Map<String, Object> disputa = travarDisputa(id);
        exigirEmAberto(disputa);
        if (disputa.get("respondida_em") != null) {
            throw new ConflitoException("disputa-ja-respondida", "Você já respondeu essa reclamação.", "texto");
        }
        jdbc.update(
                """
                UPDATE disputa SET resposta_profissional = :texto, respondida_em = :agora, status = 'em_analise'
                 WHERE id = :id
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("texto", pedido.texto().strip())
                        .addValue("agora", agora()));
        auditoria.registrar(
                eu, "PROFISSIONAL", "disputa.responder", "disputa", id, Map.of(), Map.of("respondida", true), null);
        return detalhe(id);
    }

    /** RN44/RN44a: o admin libera ao profissional ou reembolsa o cliente; falta confirmada vira ocorrência. */
    @Transactional
    DisputaDtos.Disputa decidir(UUID id, DisputaDtos.Decisao pedido) {
        UUID eu = usuario.id();
        Map<String, Object> d = daDisputa(id, "");
        UUID contrato = (UUID) d.get("contrato_id");
        UUID diaria = (UUID) d.get("diaria_id");
        if (eu.equals(d.get("cliente_id")) || eu.equals(d.get("profissional_usuario"))) {
            throw new AcaoProibidaException("disputa-propria", "Você não pode decidir uma reclamação sua.");
        }
        travar(contrato, diaria);
        Map<String, Object> disputa = travarDisputa(id);
        exigirEmAberto(disputa);
        OffsetDateTime agora = agora();
        boolean aFavorDoCliente = "cliente".equals(pedido.favor());
        boolean falta = aFavorDoCliente && "nao_compareceu".equals(disputa.get("motivo"));
        if (aFavorDoCliente) {
            reembolso.reembolsar(
                    contrato,
                    diaria,
                    StatusDiaria.CONTESTADA,
                    falta ? ReembolsoDeDiaria.Motivo.FALTA_PROFISSIONAL : ReembolsoDeDiaria.Motivo.DISPUTA,
                    eu,
                    "ADMIN",
                    agora);
        } else {
            liberacao.liberarPorDecisao(contrato, diaria, eu, agora);
        }
        jdbc.update(
                """
                UPDATE disputa SET status = :status, decidida_por = :eu, decidida_em = :agora, justificativa = :texto
                 WHERE id = :id
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("status", aFavorDoCliente ? "favor_cliente" : "favor_profissional")
                        .addValue("eu", eu)
                        .addValue("agora", agora)
                        .addValue("texto", pedido.justificativa().strip()));
        if (falta) {
            registrarFalta(id, d, eu, agora);
        }
        auditoria.registrar(
                eu,
                "ADMIN",
                "disputa.decidir",
                "disputa",
                id,
                Map.of("status", disputa.get("status")),
                Map.of("favor", pedido.favor(), "motivo", disputa.get("motivo")),
                null);
        return detalhe(id);
    }

    /** RN44c: a falta confirmada fica registrada (só inserção), auditada; a inativação é sempre manual (RN44e). */
    private void registrarFalta(UUID disputa, Map<String, Object> d, UUID admin, OffsetDateTime agora) {
        UUID profissional = (UUID) d.get("profissional_id");
        UUID ocorrencia = jdbc.queryForObject(
                """
                INSERT INTO ocorrencia_profissional (profissional_id, tipo, diaria_id, disputa_id, registrada_por,
                                                     criado_em)
                VALUES (:profissional, 'falta', :diaria, :disputa, :admin, :agora)
                RETURNING id
                """,
                new MapSqlParameterSource("profissional", profissional)
                        .addValue("diaria", d.get("diaria_id"))
                        .addValue("disputa", disputa)
                        .addValue("admin", admin)
                        .addValue("agora", agora),
                UUID.class);
        auditoria.registrar(
                admin,
                "ADMIN",
                "profissional.falta",
                "profissional",
                profissional,
                Map.of(),
                Map.of("ocorrencia", ocorrencia.toString()),
                null);
    }

    /** RN44b: depois da falta confirmada, o cliente mantém ou cancela (com reembolso integral) as outras diárias pagas. */
    @Transactional
    DisputaDtos.Disputa demaisDiarias(UUID id, DisputaDtos.DemaisDiarias pedido) {
        UUID eu = usuario.id();
        Map<String, Object> d = daDisputa(id, "AND d.cliente_id = :eu");
        UUID contrato = (UUID) d.get("contrato_id");
        jdbc.queryForList(
                "SELECT id FROM contrato WHERE id = :contrato FOR UPDATE",
                new MapSqlParameterSource("contrato", contrato),
                UUID.class);
        Map<String, Object> disputa = travarDisputa(id);
        if (!"favor_cliente".equals(disputa.get("status")) || !"nao_compareceu".equals(disputa.get("motivo"))) {
            throw new RegraDeNegocioException(
                    "sem-falta-confirmada", "Só dá para escolher depois que a COE confirmar a falta do profissional.");
        }
        if (disputa.get("demais_diarias") != null) {
            throw new ConflitoException(
                    "demais-ja-escolhidas", "Você já escolheu o que fazer com as outras diárias.", null);
        }
        OffsetDateTime agora = agora();
        int canceladas = 0;
        if ("cancelar".equals(pedido.acao())) {
            // diárias por data (a ordem das travas); só as pagas e ainda não começadas
            List<UUID> pagas = jdbc.queryForList("""
                    SELECT id FROM diaria WHERE contrato_id = :contrato AND status = 'paga'
                     ORDER BY data FOR UPDATE
                    """, new MapSqlParameterSource("contrato", contrato), UUID.class);
            for (UUID paga : pagas) {
                reembolso.reembolsar(
                        contrato,
                        paga,
                        StatusDiaria.PAGA,
                        ReembolsoDeDiaria.Motivo.FALTA_PROFISSIONAL,
                        eu,
                        "CLIENTE",
                        agora);
                canceladas++;
            }
        }
        jdbc.update(
                "UPDATE disputa SET demais_diarias = :acao, demais_escolhidas_em = :agora WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("acao", pedido.acao())
                        .addValue("agora", agora));
        auditoria.registrar(
                eu,
                "CLIENTE",
                "disputa.demais-diarias",
                "disputa",
                id,
                Map.of(),
                Map.of("acao", pedido.acao(), "canceladas", canceladas),
                null);
        return detalhe(id);
    }

    /** A fila do admin: em aberto, o prazo mais perto primeiro (RN44: decidir em até 48 h). */
    @Transactional(readOnly = true)
    DisputaDtos.Fila fila(int pagina) {
        List<DisputaDtos.ItemDaFila> itens = jdbc.query(
                """
                SELECT s.id, s.diaria_id, d.data, s.motivo, s.status, s.prazo_decisao_em, s.respondida_em,
                       uc.nome AS cliente_nome, up.nome AS profissional_nome
                  FROM disputa s
                  JOIN diaria d ON d.id = s.diaria_id
                  JOIN usuario uc ON uc.id = d.cliente_id
                  JOIN profissional p ON p.id = d.profissional_id
                  JOIN usuario up ON up.id = p.usuario_id
                 WHERE s.status IN ('aberta', 'em_analise')
                 ORDER BY s.prazo_decisao_em, s.id
                 LIMIT :limite OFFSET :inicio
                """,
                new MapSqlParameterSource("limite", POR_PAGINA + 1).addValue("inicio", (long) pagina * POR_PAGINA),
                (linha, n) -> new DisputaDtos.ItemDaFila(
                        linha.getObject("id", UUID.class),
                        linha.getObject("diaria_id", UUID.class),
                        linha.getObject("data", java.time.LocalDate.class),
                        linha.getString("motivo"),
                        linha.getString("status"),
                        linha.getTimestamp("prazo_decisao_em").toInstant(),
                        linha.getTimestamp("respondida_em") != null,
                        NomeDeExibicao.de(linha.getString("cliente_nome")),
                        NomeDeExibicao.de(linha.getString("profissional_nome"))));
        boolean temMais = itens.size() > POR_PAGINA;
        return new DisputaDtos.Fila(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional(readOnly = true)
    DisputaDtos.Disputa verComoAdmin(UUID id) {
        daDisputa(id, "");
        return detalhe(id);
    }

    /** A disputa com a diária e o contrato; o filtro de dono vem de quem chama (de outro = 404). */
    private Map<String, Object> daDisputa(UUID id, String filtro) {
        List<Map<String, Object>> linhas =
                jdbc.queryForList("""
                SELECT s.diaria_id, d.contrato_id, d.cliente_id, d.profissional_id, p.usuario_id AS profissional_usuario
                  FROM disputa s
                  JOIN diaria d ON d.id = s.diaria_id
                  JOIN profissional p ON p.id = d.profissional_id
                 WHERE s.id = :id
                """ + filtro, new MapSqlParameterSource("id", id).addValue("eu", usuario.id()));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADA);
        }
        return linhas.get(0);
    }

    /** Contrato e depois a diária, a ordem de todo fluxo; devolve o status atual da diária. */
    private StatusDiaria travar(UUID contrato, UUID diaria) {
        jdbc.queryForList(
                "SELECT id FROM contrato WHERE id = :contrato FOR UPDATE",
                new MapSqlParameterSource("contrato", contrato),
                UUID.class);
        return StatusDiaria.doCodigo(jdbc.queryForObject(
                "SELECT status FROM diaria WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", diaria),
                String.class));
    }

    private Map<String, Object> travarDisputa(UUID id) {
        return jdbc.queryForMap(
                "SELECT status, motivo, respondida_em, demais_diarias FROM disputa WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id));
    }

    private static void exigirEmAberto(Map<String, Object> disputa) {
        if (!EM_ABERTO.contains((String) disputa.get("status"))) {
            throw new ConflitoException("disputa-decidida", "Essa reclamação já foi decidida.", null);
        }
    }

    private DisputaDtos.Disputa detalhe(UUID id) {
        Map<String, Object> s = jdbc.queryForMap("""
                SELECT s.id, s.diaria_id, d.contrato_id, d.data, s.motivo, s.descricao, s.status,
                       s.resposta_profissional, s.respondida_em, s.prazo_decisao_em, s.justificativa, s.decidida_em,
                       s.demais_diarias, s.criado_em, d.valor, c.comissao_pct, c.taxa_paga_por,
                       uc.nome AS cliente_nome, up.nome AS profissional_nome
                  FROM disputa s
                  JOIN diaria d ON d.id = s.diaria_id
                  JOIN contrato c ON c.id = d.contrato_id
                  JOIN usuario uc ON uc.id = d.cliente_id
                  JOIN profissional p ON p.id = d.profissional_id
                  JOIN usuario up ON up.id = p.usuario_id
                 WHERE s.id = :id
                """, new MapSqlParameterSource("id", id));
        String status = (String) s.get("status");
        String favor = switch (status) {
            case "favor_cliente" -> "cliente";
            case "favor_profissional" -> "profissional";
            default -> null;
        };
        Dinheiro pago = CalculadoraDiaria.calcular(
                        new Dinheiro((BigDecimal) s.get("valor")),
                        new Percentual((BigDecimal) s.get("comissao_pct")),
                        TaxaPagaPor.deCodigo((String) s.get("taxa_paga_por")))
                .valorReembolso();
        boolean podeEscolher = "favor_cliente".equals(status)
                && "nao_compareceu".equals(s.get("motivo"))
                && s.get("demais_diarias") == null;
        return new DisputaDtos.Disputa(
                id,
                (UUID) s.get("diaria_id"),
                (UUID) s.get("contrato_id"),
                ((java.sql.Date) s.get("data")).toLocalDate(),
                (String) s.get("motivo"),
                (String) s.get("descricao"),
                status,
                (String) s.get("resposta_profissional"),
                instante(s.get("respondida_em")),
                instante(s.get("prazo_decisao_em")),
                favor,
                (String) s.get("justificativa"),
                instante(s.get("decidida_em")),
                (String) s.get("demais_diarias"),
                podeEscolher,
                pago,
                NomeDeExibicao.de((String) s.get("cliente_nome")),
                NomeDeExibicao.de((String) s.get("profissional_nome")),
                instante(s.get("criado_em")));
    }

    private static Instant instante(Object valor) {
        if (valor instanceof Timestamp momento) {
            return momento.toInstant();
        }
        return valor instanceof OffsetDateTime momento ? momento.toInstant() : null;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
