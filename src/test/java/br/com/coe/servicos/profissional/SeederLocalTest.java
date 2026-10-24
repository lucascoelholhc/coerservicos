package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;
import br.com.coe.servicos.config.ConfiguracaoNegocio;
import br.com.coe.servicos.usuario.ContasDeTeste;
import br.com.coe.servicos.usuario.Papel;

/** DB-11: profissionais de exemplo do perfil local, criados pelo mesmo caminho do cadastro (DOM-02). */
class SeederLocalTest extends IntegracaoTest {

    private static final AtomicLong SEQUENCIA = new AtomicLong(51_000);
    private static final String SOBRE = "Pinto casas e apartamentos com capricho, usando fita e lona.";

    @Autowired
    private ApplicationContext contexto;

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

    private SeederLocal seeder(List<ExemplosLocais.Exemplo> exemplos, String ambiente) {
        return seeder(exemplos, ambiente, arquivos);
    }

    private SeederLocal seeder(
            List<ExemplosLocais.Exemplo> exemplos, String ambiente, ArquivosDoCadastroService arquivosUsados) {
        return seeder(exemplos, ambiente, false, arquivosUsados);
    }

    private SeederLocal seeder(
            List<ExemplosLocais.Exemplo> exemplos,
            String ambiente,
            boolean comPerfilProd,
            ArquivosDoCadastroService arquivosUsados) {
        return new SeederLocal(
                exemplos,
                ambiente,
                comPerfilProd,
                cadastro,
                arquivosUsados,
                profissionais,
                fotos,
                contas,
                jdbc,
                codificador,
                transacao,
                configuracao,
                validador,
                clock);
    }

    /** Um exemplo com dados únicos deste teste (o banco não é limpo entre testes). */
    private static ExemplosLocais.Exemplo exemplo(boolean ativo, String sobre) {
        long n = SEQUENCIA.incrementAndGet();
        return new ExemplosLocais.Exemplo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Exemplo " + n,
                String.valueOf(47_900_000_000L + n),
                "exemplo" + n + "@teste.coe.local",
                CpfsDeTeste.unico().digitos(),
                LocalDate.of(1980, 5, 10),
                "pintor",
                List.of(),
                List.of(new ExemplosLocais.Servico("pintor", "Pintura interna")),
                5,
                "sim",
                false,
                "250.00",
                "140.00",
                "8h às 17h",
                4202404,
                20,
                List.of(4205902),
                Set.of(1, 2, 3),
                sobre,
                UUID.randomUUID().toString(),
                ativo,
                false,
                0x3D5A99,
                List.of(new ExemplosLocais.Foto("Sala pintada", "Pintura interna")));
    }

    @Test
    @DisplayName("no perfil de teste o seeder não existe; ele só sobe com @Profile(\"local\")")
    void soNoPerfilLocal() {
        assertThat(contexto.getBeansOfType(SeederLocal.class)).isEmpty();
        assertThat(SeederLocal.class.getAnnotation(Profile.class).value()).containsExactly("local");
    }

    @Test
    @DisplayName("sem o placeholder ambiente = local, recusa rodar e não grava nada")
    void travaDoAmbiente() {
        ExemplosLocais.Exemplo exemplo = exemplo(true, SOBRE);

        assertThatThrownBy(() -> seeder(List.of(exemplo), "test").semear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Integer.class, exemplo.usuario()))
                .isZero();
    }

    @Test
    @DisplayName("com o perfil prod junto do local, também recusa rodar")
    void nuncaComOProd() {
        ExemplosLocais.Exemplo exemplo = exemplo(true, SOBRE);

        assertThatThrownBy(
                        () -> seeder(List.of(exemplo), "local", true, arquivos).semear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Integer.class, exemplo.usuario()))
                .isZero();
    }

    @Test
    @DisplayName("não deixa usuário no contexto de segurança, nem depois de um erro")
    void contextoDeSegurancaLimpo() {
        seeder(List.of(exemplo(true, SOBRE)), "local").semear();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        ExemplosLocais.Exemplo comTelefone = exemplo(true, "Pinto casas com capricho. Chama no 47 99123-4567.");
        assertThatThrownBy(() -> seeder(List.of(comTelefone), "local").semear())
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("subida que caiu no meio deixa o rascunho; a próxima retoma e conclui")
    void retomaORascunho() {
        ExemplosLocais.Exemplo valido = exemplo(true, SOBRE);
        assertThatThrownBy(() -> seeder(List.of(valido.comRaio(15)), "local").semear())
                .isInstanceOf(IllegalStateException.class);
        assertThat(profissionais.findById(valido.profissional()).orElseThrow().status())
                .isEqualTo(Profissional.RASCUNHO);

        SeederLocal.Resultado resultado = seeder(List.of(valido), "local").semear();

        assertThat(resultado.criados()).isEqualTo(1);
        assertThat(profissionais.findById(valido.profissional()).orElseThrow().status())
                .isEqualTo(Profissional.ATIVO);
    }

    @Test
    @DisplayName("celular de exemplo já usado por outra conta: erro claro, sem cadastro pela metade")
    void celularDeOutraConta() {
        ContasDeTeste.Conta outra = new ContasDeTeste(jdbc, codificador).criar(Papel.CLIENTE);
        ExemplosLocais.Exemplo base = exemplo(true, SOBRE);
        ExemplosLocais.Exemplo repetido = new ExemplosLocais.Exemplo(
                base.usuario(),
                base.profissional(),
                base.nome(),
                outra.celular(),
                base.email(),
                base.cpf(),
                base.nascimento(),
                base.profissao(),
                base.outras(),
                base.servicos(),
                base.anos(),
                base.ferramentas(),
                base.mei(),
                base.valor(),
                base.meia(),
                base.horario(),
                base.cidade(),
                base.raio(),
                base.outrasCidades(),
                base.dias(),
                base.sobre(),
                base.pix(),
                base.ativo(),
                base.nr10(),
                base.cor(),
                base.fotos());

        assertThatThrownBy(() -> seeder(List.of(repetido), "local").semear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já usados por outra conta");
        assertThat(profissionais.findByUsuarioId(base.usuario())).isEmpty();
    }

    @Test
    @DisplayName("os 15 exemplos do protótipo: 12 ativos com o papel, 3 em análise na fila do admin")
    void semeiaOsExemplosDoPrototipo() throws Exception {
        seeder(ExemplosLocais.TODOS, "local").semear();

        List<ExemplosLocais.Exemplo> todos = ExemplosLocais.TODOS;
        assertThat(todos).hasSize(15);
        for (ExemplosLocais.Exemplo e : todos) {
            Profissional p = profissionais.findById(e.profissional()).orElseThrow();
            assertThat(p.usuarioId()).isEqualTo(e.usuario());
            assertThat(p.status()).isEqualTo(e.ativo() ? Profissional.ATIVO : Profissional.EM_ANALISE);
            assertThat(fotos.countByProfissionalId(p.id())).isEqualTo(e.fotos().size());
            List<String> papeis = jdbc.queryForList(
                    "SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, e.usuario());
            List<String> tipos = jdbc.queryForList(
                    "SELECT tipo FROM documento_verificacao WHERE profissional_id = ?", String.class, e.profissional());
            if (e.ativo()) {
                assertThat(papeis).contains("PROFISSIONAL");
                assertThat(p.nr10Status())
                        .isEqualTo(e.nr10() ? Profissional.NR10_VERIFICADO : Profissional.NR10_NAO_TEM);
            } else {
                assertThat(papeis).doesNotContain("PROFISSIONAL");
                assertThat(p.nr10Status())
                        .isEqualTo(e.nr10() ? Profissional.NR10_EM_ANALISE : Profissional.NR10_NAO_TEM);
                assertThat(tipos).contains("rg_frente", "selfie");
                assertThat(tipos.contains("certificado_nr10")).isEqualTo(e.nr10());
            }
        }
        assertThat(todos.stream().filter(ExemplosLocais.Exemplo::ativo)).hasSize(12);
        assertThat(todos.stream().filter(e -> !e.ativo() && e.nr10())).hasSize(1);
        assertThat(todos.stream().filter(e -> e.ativo() && e.nr10())).isNotEmpty();
        assertThat(todos.stream().map(ExemplosLocais.Exemplo::profissao).distinct())
                .containsExactlyInAnyOrder("pedreiro", "pintor", "eletricista", "jardineiro", "diarista");

        // pela API, como o próprio profissional: conta sem contato pendente (RN61) e cadastro do dono
        for (ExemplosLocais.Exemplo e : List.of(todos.get(0), todos.get(14))) {
            Papel[] papeis = e.ativo() ? new Papel[] {Papel.CLIENTE, Papel.PROFISSIONAL} : new Papel[] {Papel.CLIENTE};
            mockMvc.perform(get("/api/contas/eu").header("Authorization", bearer(e.usuario(), papeis)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contatoPendente").value(false))
                    .andExpect(jsonPath("$.emailConfirmado").value(true));
            mockMvc.perform(get("/api/profissional/cadastro").header("Authorization", bearer(e.usuario(), papeis)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(e.ativo() ? Profissional.ATIVO : Profissional.EM_ANALISE));
        }

        // a fila do DOM-03 mostra os 3 em análise
        UUID admin = new ContasDeTeste(jdbc, codificador).criar(Papel.ADMIN).id();
        List<String> naFila = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            String corpo = mockMvc.perform(get("/api/admin/verificacao")
                            .param("pagina", String.valueOf(pagina))
                            .header("Authorization", bearer(admin, Papel.ADMIN)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            naFila.addAll(JsonPath.read(corpo, "$.itens[*].id"));
            if (!(Boolean) JsonPath.read(corpo, "$.temMais")) {
                break;
            }
        }
        assertThat(naFila)
                .containsAll(todos.stream()
                        .filter(e -> !e.ativo())
                        .map(e -> e.profissional().toString())
                        .toList());
    }

    @Test
    @DisplayName("CPF e Pix cifrados no banco (sem o texto em claro), com o HMAC do CPF")
    void cpfEPixCifrados() {
        ExemplosLocais.Exemplo e = exemplo(true, SOBRE);

        seeder(List.of(e), "local").semear();

        byte[] cpf = jdbc.queryForObject(
                "SELECT cpf_cifrado FROM profissional WHERE id = ?", byte[].class, e.profissional());
        byte[] pix = jdbc.queryForObject(
                "SELECT chave_pix_cifrada FROM profissional WHERE id = ?", byte[].class, e.profissional());
        byte[] hash =
                jdbc.queryForObject("SELECT cpf_hash FROM profissional WHERE id = ?", byte[].class, e.profissional());
        assertThat(new String(cpf, StandardCharsets.ISO_8859_1)).doesNotContain(e.cpf());
        assertThat(new String(pix, StandardCharsets.ISO_8859_1)).doesNotContain(e.pix());
        assertThat(hash).hasSize(32);
        assertThat(profissionais.findById(e.profissional()).orElseThrow().chavePix())
                .isEqualTo(e.pix());
    }

    @Test
    @DisplayName("rodar de novo não duplica nada (usuário, profissional, fotos, documentos)")
    void idempotente() {
        ExemplosLocais.Exemplo ativo = exemplo(true, SOBRE);
        ExemplosLocais.Exemplo emAnalise = exemplo(false, SOBRE);
        SeederLocal seeder = seeder(List.of(ativo, emAnalise), "local");

        SeederLocal.Resultado primeiro = seeder.semear();
        String antes = retrato(ativo, emAnalise);
        SeederLocal.Resultado segundo = seeder.semear();

        assertThat(primeiro.criados()).isEqualTo(2);
        assertThat(segundo.criados()).isZero();
        assertThat(segundo.existentes()).isEqualTo(2);
        assertThat(retrato(ativo, emAnalise)).isEqualTo(antes);
    }

    private String retrato(ExemplosLocais.Exemplo... exemplos) {
        StringBuilder texto = new StringBuilder();
        for (ExemplosLocais.Exemplo e : exemplos) {
            texto.append(jdbc.queryForObject(
                    """
                    SELECT (SELECT count(*) FROM usuario WHERE id = ?) || '/'
                        || (SELECT count(*) FROM profissional WHERE usuario_id = ?) || '/'
                        || (SELECT count(*) FROM foto_portfolio WHERE profissional_id = ?) || '/'
                        || (SELECT count(*) FROM documento_verificacao WHERE profissional_id = ?) || '/'
                        || (SELECT count(*) FROM usuario_papel WHERE usuario_id = ?) || '/'
                        || (SELECT versao FROM profissional WHERE id = ?)
                    """,
                    String.class,
                    e.usuario(),
                    e.usuario(),
                    e.profissional(),
                    e.profissional(),
                    e.usuario(),
                    e.profissional()));
            texto.append(';');
        }
        return texto.toString();
    }

    @Test
    @DisplayName("sem o armazenamento, grava os profissionais sem arquivo (em análise continua na fila)")
    void semArmazenamento() {
        ArquivosDoCadastroService fora = mock(ArquivosDoCadastroService.class);
        when(fora.enviarFoto(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("SeaweedFS fora do ar"));
        when(fora.enviarDocumento(anyString(), any())).thenThrow(new IllegalStateException("SeaweedFS fora do ar"));
        ExemplosLocais.Exemplo ativo = exemplo(true, SOBRE);
        ExemplosLocais.Exemplo emAnalise = exemplo(false, SOBRE);

        SeederLocal.Resultado resultado =
                seeder(List.of(ativo, emAnalise), "local", fora).semear();

        assertThat(resultado.criados()).isEqualTo(2);
        assertThat(resultado.semArquivo()).isEqualTo(2);
        assertThat(profissionais.findById(ativo.profissional()).orElseThrow().status())
                .isEqualTo(Profissional.ATIVO);
        assertThat(profissionais
                        .findById(emAnalise.profissional())
                        .orElseThrow()
                        .status())
                .isEqualTo(Profissional.EM_ANALISE);
    }

    @Test
    @DisplayName("passa pelas regras do cadastro: \"Sobre\" com telefone é recusado")
    void mesmaCensuraDoCadastro() {
        ExemplosLocais.Exemplo comTelefone = exemplo(true, "Pinto casas com capricho. Chama no 47 99123-4567.");

        assertThatThrownBy(() -> seeder(List.of(comTelefone), "local").semear())
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(profissionais.findByUsuarioId(comTelefone.usuario()))
                .hasValueSatisfying(p -> assertThat(p.status()).isEqualTo(Profissional.RASCUNHO));
    }

    @Test
    @DisplayName("passa pelas regras do cadastro: menor de 18 anos é recusado")
    void mesmaIdadeMinima() {
        ExemplosLocais.Exemplo menor =
                exemplo(true, SOBRE).comNascimento(FusoDeNegocio.hoje(clock).minusYears(17));

        assertThatThrownBy(() -> seeder(List.of(menor), "local").semear())
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("18 anos");
    }

    @Test
    @DisplayName("passa pelas regras do cadastro: pedido inválido (raio fora da lista) é recusado")
    void mesmaValidacaoDosPedidos() {
        ExemplosLocais.Exemplo raioErrado = exemplo(true, SOBRE).comRaio(15);

        assertThatThrownBy(() -> seeder(List.of(raioErrado), "local").semear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raio");
    }
}
