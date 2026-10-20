package br.com.coe.servicos.profissional;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Profissionais de exemplo do perfil local (DB-11): os do protótipo, para a busca, o perfil público e a fila do
 * admin terem com o que trabalhar. Só existe com {@code @Profile("local")} e ainda confere o placeholder
 * {@code ambiente = local} do Flyway (a mesma trava do {@code R__dados_local}).
 *
 * <p>Cada exemplo passa pelo mesmo caminho do cadastro (DOM-02), como se o próprio profissional preenchesse as
 * etapas: Bean Validation dos pedidos, censura, valor mínimo, idade com Clock e CPF/Pix cifrados pelos conversores
 * (nunca SQL com bytes cifrados). Dado de exemplo errado derruba a subida, para ser corrigido. Os arquivos
 * (portfólio; documento e selfie de quem está em análise) são imagens geradas com "EXEMPLO"; sem o SeaweedFS, o
 * exemplo fica sem arquivo e o log avisa. Os ativos são aprovados direto (sem admin, {@code aprovado_por} nulo).
 * Idempotente: quem já saiu do rascunho é pulado.
 */
@Component
@Profile("local")
class SeederLocal implements ApplicationRunner {

    static final String SENHA = "coe-local-123";
    private static final Logger LOG = LoggerFactory.getLogger(SeederLocal.class);
    private static final String AMBIENTE_LOCAL = "local";

    record Resultado(int criados, int existentes, int semArquivo) {}

    private final List<ExemplosLocais.Exemplo> exemplos;
    private final String ambiente;
    private final CadastroProfissionalService cadastro;
    private final ArquivosDoCadastroService arquivos;
    private final ProfissionalRepository profissionais;
    private final FotoPortfolioRepository fotos;
    private final ContaDoCadastro contas;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder codificador;
    private final TransactionTemplate transacao;
    private final ConfiguracaoNegocio configuracao;
    private final Validator validador;
    private final Clock clock;

    @Autowired
    SeederLocal(
            @Value("${spring.flyway.placeholders.ambiente:}") String ambiente,
            CadastroProfissionalService cadastro,
            ArquivosDoCadastroService arquivos,
            ProfissionalRepository profissionais,
            FotoPortfolioRepository fotos,
            ContaDoCadastro contas,
            JdbcTemplate jdbc,
            PasswordEncoder codificador,
            TransactionTemplate transacao,
            ConfiguracaoNegocio configuracao,
            Validator validador,
            Clock clock) {
        this(
                ExemplosLocais.TODOS,
                ambiente,
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
                clock);
    }

    SeederLocal(
            List<ExemplosLocais.Exemplo> exemplos,
            String ambiente,
            CadastroProfissionalService cadastro,
            ArquivosDoCadastroService arquivos,
            ProfissionalRepository profissionais,
            FotoPortfolioRepository fotos,
            ContaDoCadastro contas,
            JdbcTemplate jdbc,
            PasswordEncoder codificador,
            TransactionTemplate transacao,
            ConfiguracaoNegocio configuracao,
            Validator validador,
            Clock clock) {
        this.exemplos = List.copyOf(exemplos);
        this.ambiente = ambiente;
        this.cadastro = cadastro;
        this.arquivos = arquivos;
        this.profissionais = profissionais;
        this.fotos = fotos;
        this.contas = contas;
        this.jdbc = jdbc;
        this.codificador = codificador;
        this.transacao = transacao;
        this.configuracao = configuracao;
        this.validador = validador;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        semear();
    }

    Resultado semear() {
        if (!AMBIENTE_LOCAL.equals(ambiente)) {
            throw new IllegalStateException(
                    "Os profissionais de exemplo só rodam no perfil local (ambiente = " + ambiente + ")");
        }
        int criados = 0;
        int existentes = 0;
        int semArquivo = 0;
        String hash = null;
        for (ExemplosLocais.Exemplo exemplo : exemplos) {
            if (jaSemeado(exemplo)) {
                existentes++;
                continue;
            }
            if (hash == null) {
                // BCrypt custo 12 uma vez só: todos os exemplos têm a mesma senha
                hash = codificador.encode(SENHA);
            }
            criarConta(exemplo, hash);
            como(exemplo, () -> preencher(exemplo));
            boolean comArquivos = como(exemplo, () -> enviarArquivos(exemplo));
            if (!comArquivos) {
                semArquivo++;
            }
            concluir(exemplo, comArquivos);
            criados++;
        }
        LOG.info(
                "Dados locais: {} profissionais de exemplo criados, {} já existiam, {} sem arquivo (logins no README)",
                criados,
                existentes,
                semArquivo);
        return new Resultado(criados, existentes, semArquivo);
    }

    private boolean jaSemeado(ExemplosLocais.Exemplo exemplo) {
        return profissionais
                .findByUsuarioId(exemplo.usuario())
                .filter(p -> !p.editavel())
                .isPresent();
    }

    /** Conta com celular confirmado (o envio exige) e papel CLIENTE, como quem se cadastrou pelo app. */
    private void criarConta(ExemplosLocais.Exemplo exemplo, String hash) {
        jdbc.update(
                """
                INSERT INTO usuario (id, nome, celular, celular_verificado_em, senha_hash, status, cidade_id)
                VALUES (?, ?, ?, ?, ?, 'ativo', (SELECT id FROM cidade WHERE codigo_ibge = ?))
                ON CONFLICT (id) DO NOTHING
                """,
                exemplo.usuario(),
                exemplo.nome(),
                exemplo.celular(),
                Timestamp.from(clock.instant()),
                hash,
                exemplo.cidade());
        jdbc.update(
                "INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'CLIENTE') ON CONFLICT DO NOTHING",
                exemplo.usuario());
    }

    /** As 9 etapas de dados, pelo serviço do cadastro e com a validação dos pedidos do controller. */
    private Void preencher(ExemplosLocais.Exemplo e) {
        // o rascunho nasce com o id fixo do exemplo; daqui em diante o serviço o encontra pelo dono
        transacao.executeWithoutResult(status -> profissionais.criarRascunhoSeNaoExiste(e.profissional(), e.usuario()));
        cadastro.trabalho(valido(new PedidosDoCadastro.Trabalho(e.profissao(), e.outras())));
        cadastro.dados(valido(new PedidosDoCadastro.Dados(e.cpf(), e.nascimento())));
        cadastro.servicos(valido(new PedidosDoCadastro.Servicos(e.servicos().stream()
                .map(s -> new PedidosDoCadastro.ServicoEscolhido(s.profissao(), s.nome()))
                .toList())));
        cadastro.experiencia(valido(new PedidosDoCadastro.Experiencia(e.anos(), e.ferramentas(), e.mei())));
        cadastro.valor(valido(new PedidosDoCadastro.Valor(
                Dinheiro.de(e.valor()), e.meia() == null ? null : Dinheiro.de(e.meia()), e.horario())));
        cadastro.onde(valido(new PedidosDoCadastro.Onde(e.cidade(), e.raio(), e.outrasCidades())));
        cadastro.dias(valido(new PedidosDoCadastro.Dias(e.dias())));
        cadastro.sobre(valido(new PedidosDoCadastro.Sobre(List.of(), e.sobre())));
        cadastro.pix(valido(new PedidosDoCadastro.Pix("aleatoria", e.pix())));
        return null;
    }

    /**
     * Fotos (se ainda não tem) e, para quem fica em análise, documento, selfie e o certificado de NR-10. Regra do
     * cadastro recusada derruba; falha do armazenamento (SeaweedFS desligado) só avisa.
     */
    private boolean enviarArquivos(ExemplosLocais.Exemplo e) {
        try {
            if (fotos.countByProfissionalId(e.profissional()) == 0) {
                for (ExemplosLocais.Foto foto : e.fotos()) {
                    arquivos.enviarFoto(foto.servico(), foto.legenda(), imagem(foto.legenda(), e.cor()));
                }
            }
            if (!e.ativo()) {
                arquivos.enviarDocumento(TipoDeDocumento.RG_FRENTE.codigo(), imagem("Documento", 0x5A5A5A));
                arquivos.enviarDocumento(TipoDeDocumento.SELFIE.codigo(), imagem("Selfie", e.cor()));
                if (e.nr10()) {
                    arquivos.enviarDocumento(
                            TipoDeDocumento.CERTIFICADO_NR10.codigo(), imagem("Certificado NR-10", 0x3B3DC4));
                }
            }
            return true;
        } catch (RegraDeNegocioException regra) {
            throw regra;
        } catch (RuntimeException armazenamento) {
            LOG.warn(
                    "Dados locais: profissional {} sem arquivos ({}). O SeaweedFS está no ar? "
                            + "(docker compose up -d seaweedfs)",
                    e.profissional(),
                    armazenamento.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Em análise com os arquivos: o envio do próprio cadastro (confere tudo de novo). Sem os arquivos, ou ativo:
     * direto na entidade, na mesma transação do papel.
     */
    private void concluir(ExemplosLocais.Exemplo e, boolean comArquivos) {
        if (!e.ativo() && comArquivos) {
            como(e, () -> cadastro.enviar(new PedidosDoCadastro.Envio(configuracao.versaoTermos(), true)));
            return;
        }
        transacao.executeWithoutResult(status -> {
            Profissional p = profissionais.travarPorId(e.profissional()).orElseThrow();
            p.enviarParaAnalise(clock.instant(), false);
            if (e.ativo()) {
                p.aprovar(null, clock.instant(), e.nr10() ? Profissional.NR10_VERIFICADO : null);
                contas.darPapelProfissional(e.usuario());
            }
            profissionais.save(p);
        });
    }

    /** O mesmo {@code @Valid} do controller; exemplo inválido é erro de quem escreveu o exemplo. */
    private <T> T valido(T pedido) {
        Set<ConstraintViolation<T>> violacoes = validador.validate(pedido);
        if (!violacoes.isEmpty()) {
            throw new IllegalStateException("Exemplo local inválido: "
                    + violacoes.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
        return pedido;
    }

    /** Roda como o dono do exemplo: os serviços do cadastro leem o usuário do token (UsuarioAutenticado). */
    private <T> T como(ExemplosLocais.Exemplo e, Supplier<T> acao) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        Jwt token = Jwt.withTokenValue("dados-locais")
                .header("alg", "none")
                .subject(e.usuario().toString())
                .build();
        contexto.setAuthentication(new JwtAuthenticationToken(token));
        SecurityContextHolder.setContext(contexto);
        try {
            return acao.get();
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }

    /** JPEG de cor sólida com "EXEMPLO" e uma legenda: nunca uma foto de verdade. */
    static byte[] imagem(String legenda, int cor) {
        BufferedImage imagem = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(cor));
            g.fillRect(0, 0, 640, 480);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
            g.drawString("EXEMPLO", 40, 220);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
            g.drawString(legenda, 40, 290);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(imagem, "jpg", saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
