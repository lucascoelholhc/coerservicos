package br.com.coe.servicos.profissional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.armazenamento.Armazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.PropriedadesDoArmazenamento;
import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.erro.AcaoProibidaException;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Verificação do cadastro pelo admin (DOM-03, RN11 a RN14). Aprovar exige o checklist inteiro; aprovar, pedir
 * correção e recusar travam a linha do profissional (antes de qualquer outra), só valem para cadastro em
 * análise e avisam por SMS. O papel PROFISSIONAL nasce aqui. Toda ação fica na auditoria, sem dado pessoal.
 */
@Service
class VerificacaoService {

    private static final Logger LOG = LoggerFactory.getLogger(VerificacaoService.class);
    private static final String CADASTRO_NAO_ENCONTRADO = "Cadastro não encontrado.";

    private final ProfissionalRepository profissionais;
    private final DocumentoVerificacaoRepository documentos;
    private final FotoPortfolioRepository fotos;
    private final FilaDeVerificacao fila;
    private final ContaDoCadastro contas;
    private final MontadorDoCadastro montador;
    private final Armazenamento armazenamento;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Duration validadeDoDocumento;
    private final Duration validadeDaFoto;

    VerificacaoService(
            ProfissionalRepository profissionais,
            DocumentoVerificacaoRepository documentos,
            FotoPortfolioRepository fotos,
            FilaDeVerificacao fila,
            ContaDoCadastro contas,
            MontadorDoCadastro montador,
            Armazenamento armazenamento,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria,
            ApplicationEventPublisher eventos,
            Clock clock,
            PropriedadesDoArmazenamento propriedades,
            @Value("${coe.cadastro.validade-url-documento:2m}") Duration validadeDoDocumento) {
        this.profissionais = profissionais;
        this.documentos = documentos;
        this.fotos = fotos;
        this.fila = fila;
        this.contas = contas;
        this.montador = montador;
        this.armazenamento = armazenamento;
        this.usuario = usuario;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.clock = clock;
        this.validadeDoDocumento = validadeDoDocumento;
        this.validadeDaFoto = propriedades.validadeUrl();
    }

    @Transactional(readOnly = true)
    VerificacaoResposta.Fila fila(int pagina) {
        return fila.pagina(pagina);
    }

    /** Ver o cadastro mostra o CPF completo: fica na auditoria (acesso a dado pessoal). */
    @Transactional
    VerificacaoResposta.Detalhe detalhe(UUID id) {
        Profissional p = enviado(id);
        auditar("profissional.verificacao-vista", "profissional", p.id(), Map.of("status", p.status()));
        return new VerificacaoResposta.Detalhe(
                p.id(),
                p.cpf() == null ? null : p.cpf().formatado(),
                contas.de(p.usuarioId()).celularConfirmado(),
                p.nr10Status(),
                p.enviadoAnaliseEm(),
                montador.montar(p, p.usuarioId()));
    }

    /** URL curta (2 min) do documento para conferir com a selfie (RN12); fica na auditoria. */
    @Transactional
    CadastroResposta.UrlResposta urlDoDocumento(UUID id, UUID documentoId) {
        Profissional p = enviado(id);
        DocumentoVerificacao documento = documentos
                .findByIdAndProfissionalId(documentoId, p.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
        URI url = armazenamento.urlAssinada(documento.chaveArquivo(), validadeDoDocumento);
        auditar(
                "profissional.documento-visto-admin",
                "documento_verificacao",
                documento.getId(),
                Map.of("tipo", documento.tipo()));
        return new CadastroResposta.UrlResposta(url.toString(), clock.instant().plus(validadeDoDocumento));
    }

    @Transactional(readOnly = true)
    CadastroResposta.UrlResposta urlDaFoto(UUID id, UUID fotoId) {
        Profissional p = enviado(id);
        FotoPortfolio foto = fotos.findByIdAndProfissionalId(fotoId, p.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada."));
        URI url = armazenamento.urlAssinada(foto.chaveArquivo(), validadeDaFoto);
        return new CadastroResposta.UrlResposta(url.toString(), clock.instant().plus(validadeDaFoto));
    }

    @Transactional
    VerificacaoResposta.Decisao aprovar(UUID id, PedidosDaVerificacao.Aprovacao pedido) {
        Profissional p = paraDecidir(id);
        if (!pedido.itens().todos()) {
            throw new RegraDeNegocioException(
                    "checklist-incompleto", "Confira todos os itens antes de aprovar o cadastro.");
        }
        String nr10 = decisaoDoNr10(p, pedido);
        UUID admin = usuario.id();
        Instant agora = clock.instant();
        for (DocumentoVerificacao documento : documentos.findByProfissionalId(p.id())) {
            if (!documento.pendente()) {
                continue;
            }
            boolean certificado = TipoDeDocumento.CERTIFICADO_NR10.codigo().equals(documento.tipo());
            if (certificado && Profissional.NR10_RECUSADO.equals(nr10)) {
                documento.recusar(admin, agora, pedido.motivoNr10().strip());
            } else {
                documento.aprovar(admin, agora);
            }
        }
        p.aprovar(admin, agora, nr10);
        profissionais.save(p);
        contas.darPapelProfissional(p.usuarioId());
        auditar("profissional.aprovar", "profissional", p.id(), Map.of("status", p.status(), "nr10", p.nr10Status()));
        avisar(p, "COE: seu cadastro foi aprovado! Entre no app para ver o seu perfil.");
        return decisao(p);
    }

    @Transactional
    VerificacaoResposta.Decisao pedirCorrecao(UUID id, PedidosDaVerificacao.Motivo pedido) {
        String motivo = motivoSemContato(pedido.motivo());
        Profissional p = paraDecidir(id);
        p.pedirCorrecao(motivo);
        profissionais.save(p);
        // o motivo é texto livre do admin: fica na linha do profissional, não na auditoria
        auditar("profissional.pedir-correcao", "profissional", p.id(), Map.of("status", p.status()));
        avisar(p, "COE: precisamos de uma correção no seu cadastro. " + motivo + " Entre no app para corrigir.");
        return decisao(p);
    }

    @Transactional
    VerificacaoResposta.Decisao recusar(UUID id, PedidosDaVerificacao.Motivo pedido) {
        String motivo = motivoSemContato(pedido.motivo());
        Profissional p = paraDecidir(id);
        p.recusar(motivo);
        profissionais.save(p);
        auditar("profissional.recusar", "profissional", p.id(), Map.of("status", p.status()));
        avisar(p, "COE: seu cadastro não foi aprovado. Motivo: " + motivo);
        return decisao(p);
    }

    /** Cadastro já enviado alguma vez (rascunho nunca chega ao admin). */
    private Profissional enviado(UUID id) {
        return profissionais
                .findById(id)
                .filter(p -> !Profissional.RASCUNHO.equals(p.status()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(CADASTRO_NAO_ENCONTRADO));
    }

    /**
     * Travado até o fim da transação: dois admins decidindo o mesmo cadastro passam um de cada vez, e o segundo
     * recebe 409. O admin não decide o próprio cadastro.
     */
    private Profissional paraDecidir(UUID id) {
        Profissional p = profissionais
                .travarPorId(id)
                .filter(encontrado -> !Profissional.RASCUNHO.equals(encontrado.status()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(CADASTRO_NAO_ENCONTRADO));
        if (p.usuarioId().equals(usuario.id())) {
            throw new AcaoProibidaException(
                    "verificacao-propria", "Outra pessoa da equipe precisa verificar o seu cadastro.");
        }
        if (!Profissional.EM_ANALISE.equals(p.status())) {
            throw new ConflitoException(
                    "verificacao-indisponivel",
                    "Esse cadastro não está mais esperando análise. Atualize a fila.",
                    null);
        }
        return p;
    }

    /** Com certificado em análise, a decisão do NR-10 é obrigatória; sem certificado, não existe. */
    private static String decisaoDoNr10(Profissional p, PedidosDaVerificacao.Aprovacao pedido) {
        boolean temCertificado = Profissional.NR10_EM_ANALISE.equals(p.nr10Status());
        String nr10 = pedido.nr10();
        if (temCertificado && nr10 == null) {
            throw new RegraDeNegocioException(
                    "nr10-sem-decisao", "Diga se o certificado de NR-10 foi conferido ou recusado.");
        }
        if (!temCertificado && nr10 != null) {
            throw new RegraDeNegocioException("nr10-sem-certificado", "Esse cadastro não mandou certificado de NR-10.");
        }
        if (Profissional.NR10_RECUSADO.equals(nr10)) {
            if (pedido.motivoNr10() == null || pedido.motivoNr10().isBlank()) {
                throw new RegraDeNegocioException(
                        "nr10-sem-motivo", "Escreva o motivo de recusar o certificado de NR-10.");
            }
            motivoSemContato(pedido.motivoNr10());
        }
        return nr10;
    }

    /**
     * O motivo vai por SMS com o nome da COE: nada de telefone, link ou e-mail (não vira golpe com o número da
     * COE). A mesma censura do cadastro (PA13).
     */
    private static String motivoSemContato(String motivo) {
        CensuraDeContato.exigirSemContato(motivo, "motivo");
        return motivo.strip();
    }

    private void auditar(String acao, String entidade, UUID id, Map<String, ?> depois) {
        UUID admin = usuario.id();
        auditoria.registrar(admin, contas.de(admin).papelPrincipal(), acao, entidade, id, null, depois, null);
    }

    /**
     * SMS depois do commit (DespachoDeMensagens), só para celular confirmado (RN61: número não provado não recebe
     * o motivo); sem ele, só o log (o app mostra o status).
     */
    private void avisar(Profissional p, String texto) {
        contas.celular(p.usuarioId())
                .ifPresentOrElse(
                        celular -> eventos.publishEvent(MensagemPronta.sms(celular, texto)),
                        () -> LOG.warn("Profissional {} sem celular para o aviso da verificação", p.id()));
    }

    private static VerificacaoResposta.Decisao decisao(Profissional p) {
        return new VerificacaoResposta.Decisao(p.id(), p.status(), p.nr10Status());
    }
}
