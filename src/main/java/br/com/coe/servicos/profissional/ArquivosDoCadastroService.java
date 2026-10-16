package br.com.coe.servicos.profissional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.armazenamento.Armazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.ArquivoValidado;
import br.com.coe.servicos.compartilhado.armazenamento.Categoria;
import br.com.coe.servicos.compartilhado.armazenamento.PropriedadesDoArmazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.ValidadorDeImagem;
import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Documento, selfie, NR-10 e fotos do cadastro (etapas 3 e 9). A imagem é validada fora da transação (o
 * processamento não segura conexão do banco) e guardada como último passo antes do commit (CORE-09). A chave do
 * arquivo nunca vem do cliente nem sai da API: só o id do registro, sempre filtrado pelo dono (anti-IDOR).
 */
@Service
class ArquivosDoCadastroService {

    static final int FOTOS_MAXIMAS = 12;
    private static final String DOCUMENTO_NAO_ENCONTRADO = "Documento não encontrado.";
    private static final String FOTO_NAO_ENCONTRADA = "Foto não encontrada.";

    private final ProfissionalRepository profissionais;
    private final DocumentoVerificacaoRepository documentos;
    private final FotoPortfolioRepository fotos;
    private final CatalogoDoCadastro catalogo;
    private final ContaDoCadastro contas;
    private final MontadorDoCadastro montador;
    private final CadastroProfissionalService cadastro;
    private final ValidadorDeImagem validador;
    private final Armazenamento armazenamento;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final Duration validadeDoDocumento;
    private final Duration validadeDaFoto;

    ArquivosDoCadastroService(
            ProfissionalRepository profissionais,
            DocumentoVerificacaoRepository documentos,
            FotoPortfolioRepository fotos,
            CatalogoDoCadastro catalogo,
            ContaDoCadastro contas,
            MontadorDoCadastro montador,
            CadastroProfissionalService cadastro,
            ValidadorDeImagem validador,
            Armazenamento armazenamento,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            TransactionTemplate transacao,
            Clock clock,
            PropriedadesDoArmazenamento propriedades,
            @Value("${coe.cadastro.validade-url-documento:2m}") Duration validadeDoDocumento) {
        this.profissionais = profissionais;
        this.documentos = documentos;
        this.fotos = fotos;
        this.catalogo = catalogo;
        this.contas = contas;
        this.montador = montador;
        this.cadastro = cadastro;
        this.validador = validador;
        this.armazenamento = armazenamento;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.clock = clock;
        this.validadeDoDocumento = validadeDoDocumento;
        this.validadeDaFoto = propriedades.validadeUrl();
    }

    CadastroResposta.DocumentoResposta enviarDocumento(String codigo, byte[] bytes) {
        TipoDeDocumento tipo = TipoDeDocumento.deCodigo(codigo);
        UUID dono = usuario.id();
        if (tipo == TipoDeDocumento.CERTIFICADO_NR10) {
            exigirNr10(profissoesDoDono(dono));
        }
        ArquivoValidado arquivo = validador.validar(bytes, tipo.categoria());
        return transacao.execute(status -> {
            Profissional p = cadastro.rascunhoEditavel();
            if (tipo == TipoDeDocumento.CERTIFICADO_NR10) {
                exigirNr10(profissoesDe(p));
            }
            // o mesmo tipo de novo substitui o anterior (o arquivo velho sai do bucket depois do commit)
            documentos.findByProfissionalIdAndTipo(p.id(), tipo.codigo()).ifPresent(velho -> {
                documentos.delete(velho);
                documentos.flush();
                armazenamento.apagar(velho.chaveArquivo());
            });
            String chave = armazenamento.guardar(arquivo, tipo.categoria());
            DocumentoVerificacao documento = documentos.save(new DocumentoVerificacao(p.id(), tipo, chave));
            auditarDocumento("profissional.documento-enviado", documento);
            return new CadastroResposta.DocumentoResposta(documento.getId(), documento.tipo(), documento.status());
        });
    }

    /** URL curta (2 min) e só do dono; o ADMIN que verifica tem a dele no DOM-03 (RN16). */
    CadastroResposta.UrlResposta urlDoDocumento(UUID id) {
        return transacao.execute(status -> {
            DocumentoVerificacao documento = documentoDoDono(id);
            URI url = armazenamento.urlAssinada(documento.chaveArquivo(), validadeDoDocumento);
            // ver documento de identidade é acesso a dado sensível: fica registrado (LGPD)
            auditarDocumento("profissional.documento-visto", documento);
            return new CadastroResposta.UrlResposta(
                    url.toString(), clock.instant().plus(validadeDoDocumento));
        });
    }

    void apagarDocumento(UUID id) {
        transacao.executeWithoutResult(status -> {
            Profissional p = cadastro.rascunhoEditavel();
            DocumentoVerificacao documento = documentos
                    .findByIdAndProfissionalId(id, p.id())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(DOCUMENTO_NAO_ENCONTRADO));
            documentos.delete(documento);
            armazenamento.apagar(documento.chaveArquivo());
            auditarDocumento("profissional.documento-removido", documento);
        });
    }

    CadastroResposta.FotoResposta enviarFoto(String servico, String legenda, byte[] bytes) {
        String texto = textoOuNulo(legenda);
        CensuraDeContato.exigirSemContato(texto, "legenda");
        UUID dono = usuario.id();
        Set<UUID> profissoes = profissoesDoDono(dono);
        UUID servicoId = servicoDaFoto(servico, profissoes);
        ArquivoValidado arquivo = validador.validar(bytes, Categoria.PORTFOLIO);
        return transacao.execute(status -> {
            // travado: dois envios ao mesmo tempo não passam juntos do limite
            Profissional p = cadastro.rascunhoEditavel();
            if (fotos.countByProfissionalId(p.id()) >= FOTOS_MAXIMAS) {
                throw new RegraDeNegocioException(
                        "limite-de-fotos", "Você já mandou " + FOTOS_MAXIMAS + " fotos. Tire uma para pôr outra.");
            }
            String chave = armazenamento.guardar(arquivo, Categoria.PORTFOLIO);
            short ordem = (short) fotos.proximaOrdem(p.id());
            FotoPortfolio foto = fotos.save(new FotoPortfolio(p.id(), servicoId, chave, texto, ordem));
            return new CadastroResposta.FotoResposta(foto.getId(), montador.nomeDoServico(foto), foto.legenda());
        });
    }

    CadastroResposta.FotoResposta descreverFoto(UUID id, PedidosDoCadastro.Foto pedido) {
        String texto = textoOuNulo(pedido.legenda());
        CensuraDeContato.exigirSemContato(texto, "legenda");
        return transacao.execute(status -> {
            Profissional p = cadastro.rascunhoEditavel();
            FotoPortfolio foto = fotos.findByIdAndProfissionalId(id, p.id())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(FOTO_NAO_ENCONTRADA));
            foto.descrever(servicoDaFoto(pedido.servico(), profissoesDe(p)), texto);
            fotos.save(foto);
            return new CadastroResposta.FotoResposta(foto.getId(), montador.nomeDoServico(foto), foto.legenda());
        });
    }

    void apagarFoto(UUID id) {
        transacao.executeWithoutResult(status -> {
            Profissional p = cadastro.rascunhoEditavel();
            FotoPortfolio foto = fotos.findByIdAndProfissionalId(id, p.id())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(FOTO_NAO_ENCONTRADA));
            fotos.delete(foto);
            armazenamento.apagar(foto.chaveArquivo());
        });
    }

    CadastroResposta.UrlResposta urlDaFoto(UUID id) {
        Profissional p = profissionais
                .findByUsuarioId(usuario.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException(FOTO_NAO_ENCONTRADA));
        FotoPortfolio foto = fotos.findByIdAndProfissionalId(id, p.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException(FOTO_NAO_ENCONTRADA));
        URI url = armazenamento.urlAssinada(foto.chaveArquivo(), validadeDaFoto);
        return new CadastroResposta.UrlResposta(url.toString(), clock.instant().plus(validadeDaFoto));
    }

    private DocumentoVerificacao documentoDoDono(UUID id) {
        Profissional p = profissionais
                .findByUsuarioId(usuario.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException(DOCUMENTO_NAO_ENCONTRADO));
        return documentos
                .findByIdAndProfissionalId(id, p.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException(DOCUMENTO_NAO_ENCONTRADO));
    }

    /** Auditoria do documento (só o tipo, nunca a chave do arquivo), na transação da ação. */
    private void auditarDocumento(String acao, DocumentoVerificacao documento) {
        UUID dono = usuario.id();
        auditoria.registrar(
                dono,
                contas.de(dono).papelPrincipal(),
                acao,
                "documento_verificacao",
                documento.getId(),
                null,
                Map.of("tipo", documento.tipo()),
                null);
    }

    /** Profissões do rascunho de quem chama, lidas numa transação curta (antes de processar a imagem). */
    private Set<UUID> profissoesDoDono(UUID dono) {
        return transacao.execute(status ->
                profissionais.findByUsuarioId(dono).map(this::profissoesDe).orElse(Set.of()));
    }

    private Set<UUID> profissoesDe(Profissional p) {
        Set<UUID> profissoes = new HashSet<>(p.outrasProfissoes());
        if (p.profissaoPrincipalId() != null) {
            profissoes.add(p.profissaoPrincipalId());
        }
        return profissoes;
    }

    private void exigirNr10(Set<UUID> profissoes) {
        boolean exige = profissoes.stream()
                .map(catalogo::profissao)
                .flatMap(Optional::stream)
                .anyMatch(CatalogoDoCadastro.Profissao::exigeNr10);
        if (!exige) {
            throw new RegraDeNegocioException(
                    "documento-tipo-invalido", "O certificado de NR-10 é só para quem trabalha como eletricista.");
        }
    }

    private UUID servicoDaFoto(String servico, Set<UUID> profissoes) {
        if (servico == null || servico.isBlank()) {
            return null;
        }
        return catalogo.servicoAtivoPeloNome(servico.strip(), profissoes)
                .orElseThrow(CadastroProfissionalService::servicoInvalido)
                .id();
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
