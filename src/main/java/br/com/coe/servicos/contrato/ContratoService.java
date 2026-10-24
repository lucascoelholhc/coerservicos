package br.com.coe.servicos.contrato;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.cripto.Campo;
import br.com.coe.servicos.compartilhado.cripto.CifraDeCampo;
import br.com.coe.servicos.compartilhado.dinheiro.CalculadoraDiaria;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.dinheiro.TotaisContrato;
import br.com.coe.servicos.compartilhado.dinheiro.ValoresDiaria;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Contratação (DOM-06, RN24–RN33). O contrato nasce aguardando pagamento, com as diárias agendadas, a comissão e
 * quem paga a taxa copiadas da configuração (congeladas: mudar a configuração não muda contrato fechado) e o
 * endereço cifrado. Os valores saem sempre da {@link CalculadoraDiaria}; o total é a soma das diárias.
 */
@Service
class ContratoService {

    /** O cliente escolhe dias livres das próximas 4 semanas a partir de amanhã (RN25). */
    private static final int DIAS_NA_JANELA = 28;

    private static final int DESCRICAO_MINIMA = 15;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String NAO_ENCONTRADO = "Contrato não encontrado.";

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioAutenticado usuario;
    private final ConfiguracaoNegocio configuracao;
    private final CifraDeCampo cifra;
    private final JsonMapper json;
    private final Clock clock;

    ContratoService(
            NamedParameterJdbcTemplate jdbc,
            UsuarioAutenticado usuario,
            ConfiguracaoNegocio configuracao,
            CifraDeCampo cifra,
            JsonMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.configuracao = configuracao;
        this.cifra = cifra;
        this.json = json;
        this.clock = clock;
    }

    private record Perfil(UUID id, UUID usuario, UUID profissao, Dinheiro diaria, Dinheiro meia, boolean lc150) {}

    @Transactional
    ContratoDtos.Contrato criar(ContratoDtos.NovoContrato pedido) {
        UUID eu = usuario.id();
        exigirContaAtiva(eu);
        ConfiguracaoNegocio.Parametros parametros = configuracao.parametros();
        if (!parametros.versaoTermos().equals(pedido.versaoTermosAceita())) {
            throw new RegraDeNegocioException(
                    "termos-desatualizados", "Os termos de uso mudaram. Leia e aceite a versão atual.");
        }
        Perfil perfil = perfil(pedido.profissionalId());
        if (perfil.usuario().equals(eu)) {
            throw new RegraDeNegocioException("contrato-proprio", "Você não pode contratar você mesmo.");
        }
        UUID cidade = cidade(pedido.endereco().cidade());
        List<UUID> servicos = servicos(perfil.id(), pedido.servicos());
        String descricao = pedido.descricao() == null || pedido.descricao().isBlank()
                ? null
                : pedido.descricao().strip();
        if (servicos.isEmpty() && (descricao == null || descricao.length() < DESCRICAO_MINIMA)) {
            throw new RegraDeNegocioException(
                    "pedido-incompleto", "Marque ao menos um serviço ou descreva o que precisa (15 letras ou mais).");
        }
        if (descricao != null) {
            CensuraDeContato.exigirSemContato(descricao, "descricao");
        }
        exigirDiasNaAgenda(perfil, pedido.dias());
        if (perfil.lc150()) {
            exigirLimiteLc150(eu, perfil, pedido.dias(), parametros.limiteDiaristaJanela7Dias());
        }

        List<ValoresDiaria> valores = new ArrayList<>();
        for (ContratoDtos.Dia dia : pedido.dias()) {
            Dinheiro valor = "meia".equals(dia.tipo()) ? perfil.meia() : perfil.diaria();
            valores.add(CalculadoraDiaria.calcular(valor, parametros.comissao(), parametros.taxaPagaPor()));
        }
        TotaisContrato totais = TotaisContrato.somar(valores);
        UUID id = UUID.randomUUID();
        OffsetDateTime agora = agora();
        jdbc.update(
                """
                INSERT INTO contrato (id, cliente_id, profissional_id, profissao_id, descricao, material, cep, cidade_id,
                                      endereco_cifrado, valor_diaria, valor_meia_diaria, comissao_pct, taxa_paga_por,
                                      valor_servicos, valor_comissao, valor_total, versao_termos, pagamento_expira_em,
                                      criado_em, atualizado_em)
                VALUES (:id, :cliente, :profissional, :profissao, :descricao, :material, :cep, :cidade, :endereco,
                        :diaria, :meia, :comissao, :taxa, :servicos, :valorComissao, :total, :termos, :expira,
                        :agora, :agora)
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("cliente", eu)
                        .addValue("profissional", perfil.id())
                        .addValue("profissao", perfil.profissao())
                        .addValue("descricao", descricao)
                        .addValue("material", pedido.material())
                        .addValue("cep", pedido.endereco().cep().replace("-", ""))
                        .addValue("cidade", cidade)
                        .addValue("endereco", cifrarEndereco(id, pedido.endereco()))
                        .addValue("diaria", perfil.diaria().valor())
                        .addValue(
                                "meia",
                                perfil.meia() == null ? null : perfil.meia().valor())
                        .addValue("comissao", parametros.comissao().valor())
                        .addValue("taxa", parametros.taxaPagaPor().codigo())
                        .addValue("servicos", totais.valorServicos().valor())
                        .addValue("valorComissao", totais.comissao().valor())
                        .addValue("total", totais.totalCliente().valor())
                        .addValue("termos", parametros.versaoTermos())
                        .addValue("expira", agora.plus(parametros.expiracaoPagamento()))
                        .addValue("agora", agora));
        for (UUID servico : servicos) {
            jdbc.update(
                    "INSERT INTO contrato_servico (contrato_id, servico_id) VALUES (:contrato, :servico)",
                    new MapSqlParameterSource("contrato", id).addValue("servico", servico));
        }
        for (int i = 0; i < pedido.dias().size(); i++) {
            agendar(id, eu, perfil.id(), pedido.dias().get(i), valores.get(i), agora);
        }
        return detalhe(id, eu);
    }

    @Transactional(readOnly = true)
    ContratoDtos.Contrato ver(UUID id) {
        return detalhe(id, usuario.id());
    }

    private void agendar(
            UUID contrato,
            UUID cliente,
            UUID profissional,
            ContratoDtos.Dia dia,
            ValoresDiaria valores,
            OffsetDateTime agora) {
        UUID diaria = UUID.randomUUID();
        try {
            jdbc.update(
                    """
                    INSERT INTO diaria (id, contrato_id, cliente_id, profissional_id, data, tipo, valor, valor_comissao,
                                        status, criado_em, atualizado_em)
                    VALUES (:id, :contrato, :cliente, :profissional, :data, :tipo, :valor, :comissao, 'agendada',
                            :agora, :agora)
                    """,
                    new MapSqlParameterSource("id", diaria)
                            .addValue("contrato", contrato)
                            .addValue("cliente", cliente)
                            .addValue("profissional", profissional)
                            .addValue("data", dia.data())
                            .addValue("tipo", dia.tipo())
                            .addValue("valor", valores.valorServico().valor())
                            .addValue("comissao", valores.comissao().valor())
                            .addValue("agora", agora));
        } catch (DuplicateKeyException ocupado) {
            // uq_diaria_agenda_profissional: outro contrato pegou o dia (também numa corrida)
            throw new ConflitoException(
                    "dia-indisponivel", "Esse profissional já tem trabalho no dia " + dia.data() + ".", "dias");
        }
        jdbc.update(
                """
                INSERT INTO historico_diaria (diaria_id, de_status, para_status, origem, ator_id, criado_em)
                VALUES (:diaria, NULL, 'agendada', 'cliente', :ator, :agora)
                """,
                new MapSqlParameterSource("diaria", diaria)
                        .addValue("ator", cliente)
                        .addValue("agora", agora));
    }

    /**
     * RN51 (LC 150/2015): a mesma diarista com o mesmo cliente tem no máximo {@code limite} diárias em qualquer janela
     * de 7 dias seguidos. A trava por (cliente, profissional) faz dois pedidos ao mesmo tempo contarem um depois do
     * outro; conta tudo que não foi cancelado nem reembolsado, de qualquer contrato.
     */
    private void exigirLimiteLc150(UUID cliente, Perfil perfil, List<ContratoDtos.Dia> dias, int limite) {
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(:chave, 0))",
                new MapSqlParameterSource("chave", "lc150:" + cliente + ":" + perfil.id()),
                Object.class);
        List<LocalDate> existentes = jdbc.queryForList(
                """
                SELECT data FROM diaria
                 WHERE cliente_id = :cliente AND profissional_id = :profissional
                   AND status NOT IN ('cancelada', 'reembolsada')
                   AND data BETWEEN :de AND :ate
                """,
                new MapSqlParameterSource("cliente", cliente)
                        .addValue("profissional", perfil.id())
                        .addValue("de", FusoDeNegocio.hoje(clock).minusDays(DIAS_NA_JANELA))
                        .addValue("ate", FusoDeNegocio.hoje(clock).plusDays(2L * DIAS_NA_JANELA)),
                LocalDate.class);
        JanelaLc150.primeiroExcesso(
                        existentes, dias.stream().map(ContratoDtos.Dia::data).toList(), limite)
                .ifPresent(dia -> {
                    throw new RegraDeNegocioException(
                            "lc150-limite",
                            "Pela lei das diaristas, a mesma diarista pode trabalhar para você até " + limite
                                    + " dias em 7 dias seguidos. O dia " + DATA.format(dia)
                                    + " passa desse limite. Escolha outro dia.");
                });
    }

    /** Dias a partir de amanhã até 4 semanas, num dia da semana trabalhado, sem bloqueio, e meia só com oferta. */
    private void exigirDiasNaAgenda(Perfil perfil, List<ContratoDtos.Dia> dias) {
        LocalDate amanha = FusoDeNegocio.hoje(clock).plusDays(1);
        LocalDate ultimo = amanha.plusDays(DIAS_NA_JANELA - 1L);
        Set<Integer> semana = new HashSet<>(jdbc.queryForList(
                "SELECT dia_semana FROM disponibilidade_semanal WHERE profissional_id = :p",
                new MapSqlParameterSource("p", perfil.id()),
                Integer.class));
        Set<LocalDate> vistos = new HashSet<>();
        for (ContratoDtos.Dia dia : dias) {
            if (!vistos.add(dia.data())) {
                throw new RegraDeNegocioException("dia-repetido", "Escolha cada dia uma vez só.");
            }
            if (dia.data().isBefore(amanha) || dia.data().isAfter(ultimo)) {
                throw new RegraDeNegocioException(
                        "dia-fora-da-janela", "Escolha dias a partir de amanhã, nas próximas 4 semanas.");
            }
            boolean bloqueado = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM bloqueio_agenda WHERE profissional_id = :p AND data = :d)",
                    new MapSqlParameterSource("p", perfil.id()).addValue("d", dia.data()),
                    Boolean.class));
            if (!semana.contains(dia.data().getDayOfWeek().getValue() % 7) || bloqueado) {
                throw new RegraDeNegocioException(
                        "dia-fora-da-agenda", "Esse profissional não trabalha no dia " + dia.data() + ".");
            }
            if ("meia".equals(dia.tipo()) && perfil.meia() == null) {
                throw new RegraDeNegocioException(
                        "meia-diaria-indisponivel", "Esse profissional não oferece meia diária.");
            }
        }
    }

    private Perfil perfil(UUID id) {
        List<Perfil> perfis = jdbc.query(
                """
                SELECT p.id, p.usuario_id, p.profissao_principal_id, p.valor_diaria, p.valor_meia_diaria,
                       pr.sujeita_lc150
                  FROM profissional p
                  JOIN usuario u ON u.id = p.usuario_id
                  JOIN profissao pr ON pr.id = p.profissao_principal_id
                 WHERE p.id = :id AND p.status = 'ativo' AND u.status = 'ativo' AND pr.ativa
                """,
                new MapSqlParameterSource("id", id),
                (linha, n) -> new Perfil(
                        linha.getObject("id", UUID.class),
                        linha.getObject("usuario_id", UUID.class),
                        linha.getObject("profissao_principal_id", UUID.class),
                        new Dinheiro(linha.getBigDecimal("valor_diaria")),
                        meia(linha.getBigDecimal("valor_meia_diaria")),
                        linha.getBoolean("sujeita_lc150")));
        if (perfis.isEmpty()) {
            throw new RecursoNaoEncontradoException("Profissional não encontrado.");
        }
        return perfis.get(0);
    }

    private static Dinheiro meia(BigDecimal valor) {
        return valor == null ? null : new Dinheiro(valor);
    }

    private UUID cidade(int codigoIbge) {
        List<UUID> cidades = jdbc.queryForList(
                "SELECT id FROM cidade WHERE codigo_ibge = :c AND ativa",
                new MapSqlParameterSource("c", codigoIbge),
                UUID.class);
        if (cidades.isEmpty()) {
            throw new RegraDeNegocioException("cidade-invalida", "A COE ainda não atende essa cidade.");
        }
        return cidades.get(0);
    }

    /** Os serviços marcados, só entre os que o profissional faz (nome igual ao do catálogo). */
    private List<UUID> servicos(UUID profissional, List<String> nomes) {
        if (nomes.isEmpty()) {
            return List.of();
        }
        Set<String> distintos = new HashSet<>(nomes);
        List<UUID> ids = jdbc.queryForList(
                """
                SELECT s.id FROM servico s JOIN profissional_servico ps ON ps.servico_id = s.id
                 WHERE ps.profissional_id = :p AND s.ativo AND s.nome IN (:nomes)
                """, new MapSqlParameterSource("p", profissional).addValue("nomes", distintos), UUID.class);
        if (ids.size() != distintos.size()) {
            throw new RegraDeNegocioException("servico-invalido", "Escolha só serviços que esse profissional faz.");
        }
        return ids;
    }

    private void exigirContaAtiva(UUID eu) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM usuario WHERE id = :eu FOR NO KEY UPDATE",
                new MapSqlParameterSource("eu", eu),
                String.class);
        if (status.isEmpty()) {
            throw new RecursoNaoEncontradoException("Conta não encontrada.");
        }
        if (!"ativo".equals(status.get(0))) {
            throw new AcaoProibidaException(
                    "conta-em-analise",
                    "Sua conta está em análise pela equipe COE. Enquanto isso, não dá para contratar.");
        }
    }

    /** Endereço amarrado ao contrato (AAD com o id): a cifra não decifra em outra linha (CORE-10). */
    private byte[] cifrarEndereco(UUID contrato, ContratoDtos.Endereco endereco) {
        ContratoDtos.EnderecoGuardado guardado = new ContratoDtos.EnderecoGuardado(
                endereco.logradouro().strip(),
                endereco.numero().strip(),
                endereco.bairro() == null ? null : endereco.bairro().strip(),
                endereco.complemento() == null ? null : endereco.complemento().strip());
        return cifra.cifrar(json.writeValueAsString(guardado), Campo.ENDERECO, "contrato:" + contrato);
    }

    private ContratoDtos.EnderecoGuardado decifrarEndereco(UUID contrato, byte[] dado) {
        return json.readValue(
                cifra.decifrar(dado, Campo.ENDERECO, "contrato:" + contrato), ContratoDtos.EnderecoGuardado.class);
    }

    /** O contrato para quem participa (cliente ou profissional); outro recebe 404 igual a inexistente. */
    private ContratoDtos.Contrato detalhe(UUID id, UUID eu) {
        List<Map<String, Object>> linhas =
                jdbc.queryForList("""
                SELECT c.id, c.numero, c.status, c.profissional_id, c.cliente_id, c.cep, c.descricao, c.material,
                       c.valor_servicos, c.valor_comissao, c.valor_total, c.pagamento_expira_em, c.pago_em,
                       c.endereco_cifrado, ci.codigo_ibge, ci.nome AS cidade_nome, ci.uf
                  FROM contrato c
                  JOIN profissional p ON p.id = c.profissional_id
                  JOIN cidade ci ON ci.id = c.cidade_id
                 WHERE c.id = :id AND (c.cliente_id = :eu OR p.usuario_id = :eu)
                """, new MapSqlParameterSource("id", id).addValue("eu", eu));
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException(NAO_ENCONTRADO);
        }
        Map<String, Object> c = linhas.get(0);
        boolean souCliente = eu.equals(c.get("cliente_id"));
        // RN29: o profissional só vê o endereço depois do pagamento; antes, só a cidade
        ContratoDtos.EnderecoGuardado endereco = souCliente || c.get("pago_em") != null
                ? decifrarEndereco(id, (byte[]) c.get("endereco_cifrado"))
                : null;
        List<ContratoDtos.DiaDoContrato> dias = jdbc.query(
                "SELECT id, data, tipo, status, valor FROM diaria WHERE contrato_id = :id ORDER BY data",
                new MapSqlParameterSource("id", id),
                (linha, n) -> new ContratoDtos.DiaDoContrato(
                        linha.getObject("id", UUID.class),
                        linha.getObject("data", LocalDate.class),
                        linha.getString("tipo"),
                        linha.getString("status"),
                        new Dinheiro(linha.getBigDecimal("valor"))));
        List<String> servicos = jdbc.queryForList("""
                SELECT s.nome FROM contrato_servico cs JOIN servico s ON s.id = cs.servico_id
                 WHERE cs.contrato_id = :id ORDER BY s.ordem, s.nome
                """, new MapSqlParameterSource("id", id), String.class);
        return new ContratoDtos.Contrato(
                id,
                ((Number) c.get("numero")).longValue(),
                (String) c.get("status"),
                (UUID) c.get("profissional_id"),
                new ContratoDtos.Cidade(
                        ((Number) c.get("codigo_ibge")).intValue(), (String) c.get("cidade_nome"), (String)
                                c.get("uf")),
                ((String) c.get("cep")).strip(),
                dias,
                servicos,
                (String) c.get("descricao"),
                (String) c.get("material"),
                new Dinheiro((BigDecimal) c.get("valor_servicos")),
                new Dinheiro((BigDecimal) c.get("valor_comissao")),
                new Dinheiro((BigDecimal) c.get("valor_total")),
                instante(c.get("pagamento_expira_em")),
                endereco);
    }

    private static Instant instante(Object valor) {
        if (valor instanceof java.sql.Timestamp momento) {
            return momento.toInstant();
        }
        return valor instanceof OffsetDateTime momento ? momento.toInstant() : null;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
