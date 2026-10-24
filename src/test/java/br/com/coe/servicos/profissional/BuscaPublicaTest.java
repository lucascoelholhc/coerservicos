package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * DOM-04: busca e perfil público (RF08, RF09, RF12, RN18 a RN23). Sem login, só ativos, nenhum contato. O relógio
 * dos testes começa na segunda-feira 05/10/2026 ({@code RelogioAjustavel}): amanhã é terça, 06/10.
 */
class BuscaPublicaTest extends IntegracaoTest {

    private static final String BUSCA = "/api/publico/profissionais";
    private static final AtomicLong SEQUENCIA = new AtomicLong(52_000);

    // cidades do lançamento (V11) longe de Blumenau, onde ficam os exemplos do seeder
    private static final int ITAJAI = 4208203;
    private static final int NAVEGANTES = 4211306;
    private static final int BALNEARIO_CAMBORIU = 4202008;
    private static final int JARAGUA_DO_SUL = 4208906;
    private static final Set<Integer> TODOS_OS_DIAS = Set.of(0, 1, 2, 3, 4, 5, 6);

    @Autowired
    private CadastroProfissionalService cadastro;

    @Autowired
    private ArquivosDoCadastroService arquivos;

    @Autowired
    private ProfissionalRepository profissionais;

    @Autowired
    private FotoPortfolioRepository fotos;

    @Autowired
    private ContaDoCadastro contas;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private ConfiguracaoNegocio configuracao;

    @Autowired
    private Validator validador;

    @Autowired
    private Clock clock;

    /** Molde de um profissional de teste, com dados únicos (o banco não é limpo entre testes). */
    private static final class Molde {
        final long n = SEQUENCIA.incrementAndGet();
        String nome = "Pessoa Teste" + n + " Sobrenome" + n;
        String profissao = "jardineiro";
        List<String> outras = List.of();
        List<ExemplosLocais.Servico> servicos = List.of(new ExemplosLocais.Servico("jardineiro", "Corte de grama"));
        int anos = 5;
        String valor = "200.00";
        String meia = null;
        int cidade = ITAJAI;
        int raio = 5;
        List<Integer> outrasCidades = List.of();
        Set<Integer> dias = TODOS_OS_DIAS;
        String sobre = "Cuido de jardins com capricho e levo minhas ferramentas.";
        boolean ativo = true;
        boolean nr10 = false;
        String ferramentas = "sim";
        final String celular = String.valueOf(47_900_000_000L + n);
        final String email = "busca" + n + "@teste.coe.local";
        final String cpf = CpfsDeTeste.unico().digitos();
        final String pix = UUID.randomUUID().toString();
        final UUID profissional = UUID.randomUUID();

        ExemplosLocais.Exemplo exemplo() {
            return new ExemplosLocais.Exemplo(
                    UUID.randomUUID(),
                    profissional,
                    nome,
                    celular,
                    email,
                    cpf,
                    LocalDate.of(1985, 3, 1),
                    profissao,
                    outras,
                    servicos,
                    anos,
                    ferramentas,
                    false,
                    valor,
                    meia,
                    "7h às 16h",
                    cidade,
                    raio,
                    outrasCidades,
                    dias,
                    sobre,
                    pix,
                    ativo,
                    nr10,
                    0x2E6B3A,
                    List.of(new ExemplosLocais.Foto(
                            "Jardim da frente", servicos.get(0).nome())));
        }
    }

    private Molde criar(Consumer<Molde> ajuste) {
        Molde molde = new Molde();
        ajuste.accept(molde);
        new SeederLocal(
                        List.of(molde.exemplo()),
                        "local",
                        false,
                        cadastro,
                        arquivos,
                        profissionais,
                        fotos,
                        contas,
                        jdbc,
                        codificador,
                        transacao,
                        configuracao,
                        validador,
                        clock)
                .semear();
        return molde;
    }

    private String corpo(MockHttpServletRequestBuilder pedido) throws Exception {
        return mockMvc.perform(pedido)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** Todas as páginas da busca: id → item (o banco tem profissionais de outros testes). */
    private Map<String, Map<String, Object>> buscar(String... parametros) throws Exception {
        Map<String, Map<String, Object>> achados = new LinkedHashMap<>();
        for (int pagina = 0; pagina < 50; pagina++) {
            String ip = "10.46." + (SEQUENCIA.incrementAndGet() % 250) + "." + (pagina + 1);
            MockHttpServletRequestBuilder pedido = get(BUSCA)
                    .param("pagina", String.valueOf(pagina))
                    .with(requisicao -> {
                        requisicao.setRemoteAddr(ip);
                        return requisicao;
                    });
            for (int i = 0; i < parametros.length; i += 2) {
                pedido.param(parametros[i], parametros[i + 1]);
            }
            String corpo = corpo(pedido);
            List<Map<String, Object>> itens = JsonPath.read(corpo, "$.itens");
            itens.forEach(item -> achados.put((String) item.get("id"), item));
            if (!(Boolean) JsonPath.read(corpo, "$.temMais")) {
                break;
            }
        }
        return achados;
    }

    private static List<String> ordemDe(Map<String, Map<String, Object>> achados, Molde... moldes) {
        Set<String> nossos = new HashSet<>();
        for (Molde m : moldes) {
            nossos.add(m.profissional.toString());
        }
        return new ArrayList<>(achados.keySet())
                .stream().filter(nossos::contains).toList();
    }

    @Test
    @DisplayName("sem login, só profissionais ativos (em análise nunca aparece)")
    void soAtivos() throws Exception {
        Molde ativo = criar(m -> {});
        Molde emAnalise = criar(m -> m.ativo = false);

        Map<String, Map<String, Object>> achados = buscar("cidade", String.valueOf(ITAJAI));

        assertThat(achados).containsKey(ativo.profissional.toString());
        assertThat(achados).doesNotContainKey(emAnalise.profissional.toString());
    }

    @Test
    @DisplayName("cidade atendida: dentro do raio da cidade base ou marcada como outra cidade (RN17, RN18)")
    void cidadeNoRaio() throws Exception {
        Molde p = criar(m -> m.outrasCidades = List.of(JARAGUA_DO_SUL));
        String id = p.profissional.toString();

        Map<String, Map<String, Object>> navegantes = buscar("cidade", String.valueOf(NAVEGANTES));
        assertThat(navegantes).containsKey(id);
        assertThat(((Number) navegantes.get(id).get("distanciaKm")).doubleValue())
                .isBetween(0.5, 5.0);
        // Balneário Camboriú fica a uns 10 km de Itajaí: fora do raio de 5 km
        assertThat(buscar("cidade", String.valueOf(BALNEARIO_CAMBORIU))).doesNotContainKey(id);
        assertThat(buscar("cidade", String.valueOf(JARAGUA_DO_SUL))).containsKey(id);
        // cidade fora do catálogo: ninguém (sem erro)
        assertThat(buscar("cidade", "1234567")).isEmpty();
    }

    @Test
    @DisplayName("filtros: profissão (principal ou outra), serviço sem acento, valor máximo, experiência e meia diária")
    void filtros() throws Exception {
        Molde pedreiroPintor = criar(m -> {
            m.cidade = JARAGUA_DO_SUL;
            m.profissao = "pedreiro";
            m.outras = List.of("pintor");
            m.servicos = List.of(
                    new ExemplosLocais.Servico("pedreiro", "Reboco"),
                    new ExemplosLocais.Servico("pintor", "Pintura de muro"));
            m.valor = "300.00";
            m.anos = 10;
        });
        Molde diarista = criar(m -> {
            m.cidade = JARAGUA_DO_SUL;
            m.profissao = "diarista";
            m.servicos = List.of(new ExemplosLocais.Servico("diarista", "Pós-obra"));
            m.valor = "180.00";
            m.meia = "100.00";
            m.anos = 3;
        });
        String cidade = String.valueOf(JARAGUA_DO_SUL);
        String pp = pedreiroPintor.profissional.toString();
        String di = diarista.profissional.toString();

        assertThat(buscar("cidade", cidade, "profissao", "pintor"))
                .containsKey(pp)
                .doesNotContainKey(di);
        assertThat(buscar("cidade", cidade, "profissao", "pedreiro"))
                .containsKey(pp)
                .doesNotContainKey(di);
        assertThat(buscar("cidade", cidade, "servico", "pos-obra"))
                .containsKey(di)
                .doesNotContainKey(pp);
        assertThat(buscar("cidade", cidade, "valorMaximo", "200.00"))
                .containsKey(di)
                .doesNotContainKey(pp);
        assertThat(buscar("cidade", cidade, "experienciaMinima", "5"))
                .containsKey(pp)
                .doesNotContainKey(di);
        assertThat(buscar("cidade", cidade, "meiaDiaria", "true"))
                .containsKey(di)
                .doesNotContainKey(pp);
        // profissão desconhecida: ninguém
        assertThat(buscar("profissao", "astronauta")).isEmpty();
    }

    @Test
    @DisplayName("filtro de ferramentas: sim = traz todas; parte = traz ao menos parte")
    void ferramentas() throws Exception {
        Molde todas = criar(m -> m.cidade = BALNEARIO_CAMBORIU);
        Molde parte = criar(m -> {
            m.cidade = BALNEARIO_CAMBORIU;
            m.ferramentas = "parte";
        });
        Molde nenhuma = criar(m -> {
            m.cidade = BALNEARIO_CAMBORIU;
            m.ferramentas = "nao";
        });
        String cidade = String.valueOf(BALNEARIO_CAMBORIU);

        assertThat(buscar("cidade", cidade, "ferramentas", "sim"))
                .containsKey(todas.profissional.toString())
                .doesNotContainKeys(parte.profissional.toString(), nenhuma.profissional.toString());
        assertThat(buscar("cidade", cidade, "ferramentas", "parte"))
                .containsKeys(todas.profissional.toString(), parte.profissional.toString())
                .doesNotContainKey(nenhuma.profissional.toString());
    }

    @Test
    @DisplayName("ordem: livre antes (empate: mais experiência), menor preço e experiência (RN21)")
    void ordenacao() throws Exception {
        // amanhã é terça (dia 2); quem só trabalha sábado (6) fica livre depois
        Molde sabado = criar(m -> {
            m.dias = Set.of(6);
            m.valor = "150.00";
            m.anos = 20;
        });
        Molde livreExperiente = criar(m -> {
            m.valor = "250.00";
            m.anos = 10;
        });
        Molde livreNovato = criar(m -> {
            m.valor = "200.00";
            m.anos = 1;
        });
        String cidade = String.valueOf(ITAJAI);

        Map<String, Map<String, Object>> livre = buscar("cidade", cidade);
        assertThat(ordemDe(livre, sabado, livreExperiente, livreNovato))
                .containsExactly(
                        livreExperiente.profissional.toString(),
                        livreNovato.profissional.toString(),
                        sabado.profissional.toString());
        assertThat(livre.get(livreExperiente.profissional.toString()).get("proximoDiaLivre"))
                .isEqualTo("2026-10-06");
        assertThat(livre.get(sabado.profissional.toString()).get("proximoDiaLivre"))
                .isEqualTo("2026-10-10");

        assertThat(ordemDe(buscar("cidade", cidade, "ordem", "preco"), sabado, livreExperiente, livreNovato))
                .containsExactly(
                        sabado.profissional.toString(),
                        livreNovato.profissional.toString(),
                        livreExperiente.profissional.toString());
        assertThat(ordemDe(buscar("cidade", cidade, "ordem", "experiencia"), sabado, livreExperiente, livreNovato))
                .containsExactly(
                        sabado.profissional.toString(),
                        livreExperiente.profissional.toString(),
                        livreNovato.profissional.toString());
    }

    @Test
    @DisplayName("dia livre: dia da semana que trabalha, sem bloqueio na agenda e sem diária marcada")
    void diaLivre() throws Exception {
        Molde p = criar(m -> m.cidade = NAVEGANTES);
        String id = p.profissional.toString();
        jdbc.update(
                "INSERT INTO bloqueio_agenda (profissional_id, data) VALUES (?, ?), (?, ?)",
                p.profissional,
                LocalDate.of(2026, 10, 6),
                p.profissional,
                LocalDate.of(2026, 10, 7));
        marcarDiaria(p.profissional, LocalDate.of(2026, 10, 8));
        String cidade = String.valueOf(NAVEGANTES);

        assertThat(buscar("cidade", cidade, "dia", "2026-10-07")).doesNotContainKey(id);
        assertThat(buscar("cidade", cidade, "dia", "2026-10-08")).doesNotContainKey(id);
        Map<String, Map<String, Object>> livreNoDia9 = buscar("cidade", cidade, "dia", "2026-10-09");
        assertThat(livreNoDia9).containsKey(id);
        assertThat(livreNoDia9.get(id).get("proximoDiaLivre")).isEqualTo("2026-10-09");
    }

    /** Uma diária paga no dia (o contrato mínimo que o banco aceita). */
    private void marcarDiaria(UUID profissional, LocalDate dia) {
        UUID cliente =
                jdbc.queryForObject("""
                INSERT INTO usuario (nome, celular, senha_hash, status)
                VALUES ('Cliente da diária', ?, 'x', 'ativo') RETURNING id""", UUID.class, String.valueOf(47_900_000_000L + SEQUENCIA.incrementAndGet()));
        UUID contrato = jdbc.queryForObject("""
                INSERT INTO contrato (cliente_id, profissional_id, profissao_id, status, material, cep, cidade_id,
                    endereco_cifrado, valor_diaria, comissao_pct, taxa_paga_por, valor_servicos, valor_comissao,
                    valor_total, versao_termos, pagamento_expira_em, pago_em)
                SELECT ?, p.id, p.profissao_principal_id, 'pago', 'combinar', '88370000', p.cidade_base_id,
                    decode('00', 'hex'), 200.00, 0.1000, 'cliente', 200.00, 20.00, 220.00, '1.0', now(), now()
                  FROM profissional p WHERE p.id = ?
                RETURNING id""", UUID.class, cliente, profissional);
        jdbc.update("""
                INSERT INTO diaria (contrato_id, cliente_id, profissional_id, data, valor, valor_comissao, status)
                VALUES (?, ?, ?, ?, 200.00, 20.00, 'paga')""", contrato, cliente, profissional, dia);
    }

    @Test
    @DisplayName("dia fora da janela (hoje, passado ou longe demais) e valor inválido: 422")
    void pedidoInvalido() throws Exception {
        mockMvc.perform(get(BUSCA).param("dia", "2026-10-05"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-invalido"));
        mockMvc.perform(get(BUSCA).param("dia", "2027-03-01"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:dia-invalido"));
        mockMvc.perform(get(BUSCA).param("valorMaximo", "abc"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:valor-invalido"));
        mockMvc.perform(get(BUSCA).param("ordem", "nota"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:ordem-invalida"));
        mockMvc.perform(get(BUSCA).param("dia", "amanha")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BUSCA).param("valorMaximo", "0.00"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:valor-invalido"));
        mockMvc.perform(get(BUSCA).param("experienciaMinima", "-1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:filtro-invalido"));
        mockMvc.perform(get(BUSCA).param("servico", "x".repeat(81)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:filtro-invalido"));
        mockMvc.perform(get(BUSCA).param("ferramentas", "talvez"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:filtro-invalido"));
        // página fora da faixa é ajustada, não dá erro
        mockMvc.perform(get(BUSCA).param("pagina", "-3")).andExpect(status().isOk());
        mockMvc.perform(get(BUSCA).param("pagina", "999999")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("perfil público: trabalho, dias livres, \"Sobre\", fotos com URL curta, selo NR-10, Novo na COE")
    @SuppressWarnings("unchecked")
    void perfil() throws Exception {
        Molde p = criar(m -> {
            m.cidade = NAVEGANTES;
            m.profissao = "eletricista";
            m.servicos = List.of(new ExemplosLocais.Servico("eletricista", "Tomadas e interruptores"));
            m.nr10 = true;
            m.dias = Set.of(1, 2, 3, 4, 5);
            m.outrasCidades = List.of(ITAJAI);
            m.meia = "120.00";
        });
        jdbc.update(
                "INSERT INTO bloqueio_agenda (profissional_id, data) VALUES (?, ?)",
                p.profissional,
                LocalDate.of(2026, 10, 7));

        String corpo = corpo(get(BUSCA + "/" + p.profissional));

        assertThat((String) JsonPath.read(corpo, "$.nome")).isEqualTo("Pessoa S.");
        assertThat((String) JsonPath.read(corpo, "$.profissao")).isEqualTo("eletricista");
        assertThat((List<String>) JsonPath.read(corpo, "$.servicos[*].nome"))
                .containsExactly("Tomadas e interruptores");
        assertThat((String) JsonPath.read(corpo, "$.valorDiaria")).isEqualTo("200.00");
        assertThat((String) JsonPath.read(corpo, "$.valorMeiaDiaria")).isEqualTo("120.00");
        assertThat((Integer) JsonPath.read(corpo, "$.experienciaAnos")).isEqualTo(5);
        assertThat((String) JsonPath.read(corpo, "$.horario")).isEqualTo("7h às 16h");
        assertThat((String) JsonPath.read(corpo, "$.ferramentas")).isEqualTo("sim");
        assertThat((Integer) JsonPath.read(corpo, "$.cidade.codigoIbge")).isEqualTo(NAVEGANTES);
        assertThat((Integer) JsonPath.read(corpo, "$.raioKm")).isEqualTo(5);
        assertThat((List<Integer>) JsonPath.read(corpo, "$.outrasCidades[*].codigoIbge"))
                .containsExactly(ITAJAI);
        assertThat((List<Integer>) JsonPath.read(corpo, "$.diasDaSemana")).containsExactly(1, 2, 3, 4, 5);
        List<String> diasLivres = JsonPath.read(corpo, "$.diasLivres");
        // quatro semanas a partir de amanhã, só seg a sex, sem o dia bloqueado
        assertThat(diasLivres).startsWith("2026-10-06", "2026-10-08", "2026-10-09", "2026-10-12");
        assertThat(diasLivres).doesNotContain("2026-10-07", "2026-10-10", "2026-10-11", "2026-10-05");
        assertThat(diasLivres).hasSize(19).last().isEqualTo("2026-11-02");
        assertThat((String) JsonPath.read(corpo, "$.sobre")).isEqualTo(p.sobre);
        assertThat((Boolean) JsonPath.read(corpo, "$.seloNr10")).isTrue();
        assertThat((Boolean) JsonPath.read(corpo, "$.avaliacoes.novoNaCoe")).isTrue();
        assertThat((Integer) JsonPath.read(corpo, "$.avaliacoes.total")).isZero();
        assertThat((Object) JsonPath.read(corpo, "$.avaliacoes.nota")).isNull();
        assertThat((List<String>) JsonPath.read(corpo, "$.fotos[*].legenda")).containsExactly("Jardim da frente");
        assertThat((String) JsonPath.read(corpo, "$.fotos[0].url")).contains("/portfolio/");
        assertThat((String) JsonPath.read(corpo, "$.fotos[0].expiraEm")).isNotBlank();
    }

    @Test
    @DisplayName("foto ocultada pelo admin não sai no perfil; a resposta não fica em cache (URL assinada)")
    void fotoOcultaENoStore() throws Exception {
        Molde p = criar(m -> m.cidade = NAVEGANTES);
        jdbc.update("UPDATE foto_portfolio SET visivel = false WHERE profissional_id = ?", p.profissional);

        mockMvc.perform(get(BUSCA + "/" + p.profissional))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos").isEmpty())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("perfil de quem não está ativo (ou não existe) = 404, mesmo corpo")
    void perfilSoDeAtivo() throws Exception {
        Molde emAnalise = criar(m -> m.ativo = false);

        String deEmAnalise = mockMvc.perform(get(BUSCA + "/" + emAnalise.profissional))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String deInexistente = mockMvc.perform(get(BUSCA + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // só o instance (o caminho com o id) muda
        for (String campo : List.of("$.type", "$.title", "$.detail", "$.status")) {
            assertThat((Object) JsonPath.read(deEmAnalise, campo)).isEqualTo(JsonPath.read(deInexistente, campo));
        }
        mockMvc.perform(get(BUSCA + "/nao-e-um-id")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nenhum contato nem dado pessoal na busca e no perfil (celular, e-mail, CPF, Pix, sobrenome)")
    void semContato() throws Exception {
        Molde p = criar(m -> {
            m.cidade = BALNEARIO_CAMBORIU;
            m.nome = "Zuleica Varredura" + m.n + " Sobrenomeoculto" + m.n;
        });

        String busca = corpo(get(BUSCA).param("cidade", String.valueOf(BALNEARIO_CAMBORIU)));
        String perfil = corpo(get(BUSCA + "/" + p.profissional));

        for (String resposta : List.of(busca, perfil)) {
            assertThat(resposta)
                    .doesNotContain(p.celular)
                    .doesNotContain(p.celular.substring(2))
                    .doesNotContain(p.email)
                    .doesNotContain(p.cpf)
                    .doesNotContain(p.cpf.substring(0, 9))
                    .doesNotContain(p.pix)
                    .doesNotContain("Sobrenomeoculto" + p.n)
                    .doesNotContain("Varredura" + p.n)
                    .doesNotContain("usuarioId")
                    .doesNotContainIgnoringCase("\"cpf")
                    .doesNotContainIgnoringCase("\"celular")
                    .doesNotContainIgnoringCase("\"email")
                    .doesNotContainIgnoringCase("\"telefone")
                    .doesNotContainIgnoringCase("\"pix")
                    .doesNotContainIgnoringCase("\"chavePix")
                    .doesNotContainIgnoringCase("\"endereco")
                    .doesNotContainIgnoringCase("nascimento")
                    .doesNotContainIgnoringCase("documento")
                    .doesNotContainIgnoringCase("selfie");
        }
        assertThat(perfil).contains("Zuleica S.");
    }

    @Test
    @DisplayName("público de verdade: token vencido ou inválido no header não barra a busca nem o perfil")
    void tokenInvalidoNaoBarra() throws Exception {
        Molde p = criar(m -> m.cidade = NAVEGANTES);

        mockMvc.perform(get(BUSCA).header("Authorization", "Bearer token-quebrado"))
                .andExpect(status().isOk());
        mockMvc.perform(get(BUSCA + "/" + p.profissional).header("Authorization", "Bearer token-quebrado"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("limite por IP dos GETs públicos: passou, 429 com Retry-After")
    void limitePorIp() throws Exception {
        String ip = "10.44.0." + (SEQUENCIA.incrementAndGet() % 200);
        int recusas = 0;
        for (int i = 0; i < 70; i++) {
            int codigo = mockMvc.perform(get(BUSCA + "/" + UUID.randomUUID()).with(requisicao -> {
                        requisicao.setRemoteAddr(ip);
                        return requisicao;
                    }))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (codigo == 429) {
                recusas++;
            }
        }
        assertThat(recusas).isPositive();
        mockMvc.perform(get(BUSCA).with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
