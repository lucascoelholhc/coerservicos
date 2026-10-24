package br.com.coe.servicos.profissional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.censura.CensuraDeContato;
import br.com.coe.servicos.compartilhado.cripto.HmacDeCpf;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Cadastro do profissional em etapas (DOM-02, RN08 a RN10, RN17). Só o próprio rascunho: o dono vem do token.
 * Cada etapa salva o seu pedaço; o envio confere tudo de novo e manda para análise (fora da busca até a
 * aprovação do DOM-03, que é quem dá o papel PROFISSIONAL). Em análise, nada muda (decisão de 16/10).
 */
@Service
class CadastroProfissionalService {

    static final int IDADE_MINIMA = 18;
    static final String MENSAGEM_IDADE = "Para trabalhar na COE é preciso ter 18 anos ou mais.";
    private static final Set<Integer> FAIXAS_DE_EXPERIENCIA = Set.of(0, 1, 3, 5, 10, 20);
    private static final LocalDate NASCIMENTO_MINIMO = LocalDate.of(1900, 1, 2);

    private final ProfissionalRepository profissionais;
    private final DocumentoVerificacaoRepository documentos;
    private final CatalogoDoCadastro catalogo;
    private final ContaDoCadastro contas;
    private final MontadorDoCadastro montador;
    private final UsuarioAutenticado usuario;
    private final HmacDeCpf hmac;
    private final ConfiguracaoNegocio configuracao;
    private final RegistroDeAuditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock clock;

    CadastroProfissionalService(
            ProfissionalRepository profissionais,
            DocumentoVerificacaoRepository documentos,
            CatalogoDoCadastro catalogo,
            ContaDoCadastro contas,
            MontadorDoCadastro montador,
            UsuarioAutenticado usuario,
            HmacDeCpf hmac,
            ConfiguracaoNegocio configuracao,
            RegistroDeAuditoria auditoria,
            TransactionTemplate transacao,
            Clock clock) {
        this.profissionais = profissionais;
        this.documentos = documentos;
        this.catalogo = catalogo;
        this.contas = contas;
        this.montador = montador;
        this.usuario = usuario;
        this.hmac = hmac;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    CadastroResposta buscar() {
        UUID dono = usuario.id();
        Profissional p = profissionais
                .findByUsuarioId(dono)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Você ainda não começou o cadastro."));
        return montador.montar(p, dono);
    }

    @Transactional
    CadastroResposta trabalho(PedidosDoCadastro.Trabalho pedido) {
        Profissional p = rascunhoEditavel();
        CatalogoDoCadastro.Profissao principal = profissaoAtiva(pedido.profissao());
        Set<UUID> outras = new HashSet<>();
        for (String codigo : pedido.outras()) {
            outras.add(profissaoAtiva(codigo).id());
        }
        outras.remove(principal.id());
        p.definirTrabalho(principal.id(), outras);
        // serviço de profissão que saiu do cadastro sai junto
        Set<UUID> permitidas = profissoesDe(p);
        Set<UUID> servicos = new HashSet<>();
        for (UUID servico : p.servicos()) {
            catalogo.servico(servico)
                    .filter(s -> permitidas.contains(s.profissaoId()))
                    .ifPresent(s -> servicos.add(s.id()));
        }
        p.definirServicos(servicos);
        return salvar(p);
    }

    /**
     * CPF e nascimento. Fora de uma transação só: a conferência do CPF repetido grava a auditoria numa transação
     * curta e própria (senão o 409 desfaria a auditoria), e a corrida entre dois cadastros cai no índice único.
     */
    CadastroResposta dados(PedidosDoCadastro.Dados pedido) {
        Cpf cpf = Cpf.de(pedido.cpf());
        exigirIdade(pedido.dataNascimento());
        UUID dono = usuario.id();
        // em análise responde isso, e não o 409 do CPF (nem consulta o CPF de quem não pode editar)
        profissionais.findByUsuarioId(dono).filter(p -> !p.editavel()).ifPresent(p -> {
            throw emAnalise();
        });
        // Contra enumeração de CPF pelo 409: só quem confirmou um celular (custa um chip) chega a consultar o CPF
        // (revisão de segurança da fatia 2). O envio confere de novo.
        if (!contas.de(dono).celularConfirmado()) {
            throw new RegraDeNegocioException(
                    "celular-nao-confirmado", "Confirme seu celular antes de informar o CPF.");
        }
        if (profissionais.cpfEmUsoPorOutro(hmac.de(cpf), dono)) {
            throw cpfRepetido(dono);
        }
        try {
            return transacao.execute(status -> {
                Profissional p = rascunhoEditavel();
                p.definirCpf(cpf, hmac, pedido.dataNascimento());
                CadastroResposta resposta = salvar(p);
                auditar(dono, "profissional.dados-alterados", p.id(), Map.of("campos", List.of("cpf", "nascimento")));
                return resposta;
            });
        } catch (DataIntegrityViolationException corrida) {
            if (String.valueOf(corrida.getMostSpecificCause().getMessage()).contains("uq_profissional_cpf")) {
                throw cpfRepetido(dono);
            }
            throw corrida;
        }
    }

    @Transactional
    CadastroResposta servicos(PedidosDoCadastro.Servicos pedido) {
        Profissional p = rascunhoEditavel();
        Set<UUID> permitidas = profissoesDe(p);
        Set<UUID> servicos = new HashSet<>();
        for (PedidosDoCadastro.ServicoEscolhido escolhido : pedido.servicos()) {
            servicos.add(catalogo.servicoAtivo(escolhido.profissao(), escolhido.nome(), permitidas)
                    .orElseThrow(CadastroProfissionalService::servicoInvalido)
                    .id());
        }
        p.definirServicos(servicos);
        return salvar(p);
    }

    @Transactional
    CadastroResposta experiencia(PedidosDoCadastro.Experiencia pedido) {
        if (!FAIXAS_DE_EXPERIENCIA.contains(pedido.anos())) {
            throw new RegraDeNegocioException("experiencia-invalida", "Escolha uma das faixas de experiência.");
        }
        Profissional p = rascunhoEditavel();
        p.definirExperiencia(pedido.anos().shortValue(), pedido.ferramentas(), Boolean.TRUE.equals(pedido.mei()));
        return salvar(p);
    }

    @Transactional
    CadastroResposta valor(PedidosDoCadastro.Valor pedido) {
        exigirValorMinimo(pedido.valorDiaria(), pedido.valorMeiaDiaria());
        CensuraDeContato.exigirSemContato(pedido.horario(), "horario");
        Profissional p = rascunhoEditavel();
        p.definirValor(
                pedido.valorDiaria(), pedido.valorMeiaDiaria(), pedido.horario().strip());
        return salvar(p);
    }

    @Transactional
    CadastroResposta onde(PedidosDoCadastro.Onde pedido) {
        UUID base = cidadeAtiva(pedido.cidade());
        Set<UUID> outras = new HashSet<>();
        for (Integer codigo : pedido.outrasCidades()) {
            outras.add(cidadeAtiva(codigo));
        }
        outras.remove(base);
        Profissional p = rascunhoEditavel();
        p.definirOnde(base, pedido.raioKm().shortValue(), outras);
        return salvar(p);
    }

    @Transactional
    CadastroResposta dias(PedidosDoCadastro.Dias pedido) {
        Set<Short> dias = new HashSet<>();
        pedido.dias().forEach(dia -> dias.add(dia.shortValue()));
        Profissional p = rascunhoEditavel();
        p.definirDias(dias);
        return salvar(p);
    }

    @Transactional
    CadastroResposta sobre(PedidosDoCadastro.Sobre pedido) {
        List<String> frases = new ArrayList<>();
        for (String frase : pedido.frases()) {
            CensuraDeContato.exigirSemContato(frase, "frases");
            frases.add(frase.strip());
        }
        String texto = pedido.texto() == null || pedido.texto().isBlank()
                ? null
                : pedido.texto().strip();
        CensuraDeContato.exigirSemContato(texto, "texto");
        Profissional p = rascunhoEditavel();
        p.definirSobre(frases, texto);
        int minimo = configuracao.sobreMinimoCaracteres();
        if (p.textoSobre().length() < minimo) {
            throw new RegraDeNegocioException(
                    "sobre-curto", "Conte um pouco mais sobre você: pelo menos " + minimo + " letras.");
        }
        return salvar(p);
    }

    @Transactional
    CadastroResposta pix(PedidosDoCadastro.Pix pedido) {
        String chave = ChavesPix.normalizar(pedido.tipo(), pedido.chave());
        Profissional p = rascunhoEditavel();
        p.definirPix(pedido.tipo(), chave);
        CadastroResposta resposta = salvar(p);
        // só o tipo: a chave é dado pessoal (CORE-11)
        auditar(usuario.id(), "profissional.pix-alterado", p.id(), Map.of("tipo", pedido.tipo()));
        return resposta;
    }

    @Transactional
    CadastroResposta enviar(PedidosDoCadastro.Envio pedido) {
        UUID dono = usuario.id();
        Profissional p = rascunhoEditavel();
        String antes = p.status();
        if (!configuracao.versaoTermos().equals(pedido.versaoTermosAceita())) {
            throw new RegraDeNegocioException(
                    "termos-desatualizados", "Os termos de uso mudaram. Leia e aceite a versão atual.");
        }
        Map<String, Boolean> completas = montador.completas(p);
        List<String> faltas = montador.faltas(completas);
        if (!faltas.isEmpty()) {
            throw new RegraDeNegocioException(
                    "cadastro-incompleto", "Falta completar: " + String.join(", ", faltas) + ".");
        }
        exigirValorMinimo(p.valorDiaria(), p.valorMeiaDiaria());
        // um telefone partido entre a frase e o texto só aparece com os dois juntos
        CensuraDeContato.exigirSemContato(p.textoSobre(), "sobre");
        ContaDoCadastro.Conta conta = contas.de(dono);
        if (!conta.celularConfirmado()) {
            throw new RegraDeNegocioException(
                    "celular-nao-confirmado", "Confirme seu celular antes de enviar o cadastro.");
        }
        exigirIdade(p.dataNascimento());
        if (!p.cpfConfere(hmac)) {
            // a cifra do CPF não bate com o HMAC da linha: dado mexido no banco, nunca erro de quem envia
            throw new IllegalStateException("CPF do cadastro não confere com o HMAC (profissional " + p.id() + ")");
        }
        boolean temNr10 = documentos
                .findByProfissionalIdAndTipo(p.id(), TipoDeDocumento.CERTIFICADO_NR10.codigo())
                .isPresent();
        p.enviarParaAnalise(clock.instant(), temNr10);
        profissionais.save(p);
        auditoria.registrar(
                dono,
                conta.papelPrincipal(),
                "profissional.enviado-analise",
                "profissional",
                p.id(),
                Map.of("status", antes),
                Map.of("status", Profissional.EM_ANALISE),
                null);
        return montador.montar(p, dono);
    }

    /**
     * O rascunho de quem chama, criado na primeira etapa (ou no primeiro upload) e travado até o fim da transação;
     * em análise ou aprovado não muda. Precisa de transação aberta.
     */
    Profissional rascunhoEditavel() {
        UUID dono = usuario.id();
        Profissional p = profissionais.travarPorUsuario(dono).orElseGet(() -> {
            profissionais.criarRascunhoSeNaoExiste(UUID.randomUUID(), dono);
            return profissionais.travarPorUsuario(dono).orElseThrow();
        });
        if (!p.editavel()) {
            throw emAnalise();
        }
        return p;
    }

    private static RegraDeNegocioException emAnalise() {
        return new RegraDeNegocioException(
                "cadastro-em-analise", "Seu cadastro está em análise. Espere a resposta para mudar alguma coisa.");
    }

    /** Diária e meia diária dentro dos mínimos de hoje (RN09); conferido na etapa e de novo no envio. */
    private void exigirValorMinimo(Dinheiro diaria, Dinheiro meia) {
        ConfiguracaoNegocio.Parametros parametros = configuracao.parametros();
        if (diaria.compareTo(parametros.diariaMinima()) < 0) {
            throw new RegraDeNegocioException(
                    "valor-abaixo-do-minimo",
                    "O valor mínimo da diária é " + parametros.diariaMinima().formatar() + ".");
        }
        if (meia != null && (meia.compareTo(parametros.meiaDiariaMinima()) < 0 || meia.compareTo(diaria) >= 0)) {
            throw new RegraDeNegocioException(
                    "meia-diaria-invalida",
                    "A meia diária precisa ser de pelo menos "
                            + parametros.meiaDiariaMinima().formatar() + " e menor que a diária.");
        }
    }

    /** Auditoria de mudança no cadastro, só com dado não pessoal, na transação da ação. */
    private void auditar(UUID dono, String acao, UUID profissional, Map<String, ?> depois) {
        auditoria.registrar(
                dono, contas.de(dono).papelPrincipal(), acao, "profissional", profissional, null, depois, null);
    }

    private CadastroResposta salvar(Profissional p) {
        p.definirEtapaAtual(montador.etapaAtual(montador.completas(p)));
        profissionais.saveAndFlush(p);
        return montador.montar(p, usuario.id());
    }

    private Set<UUID> profissoesDe(Profissional p) {
        Set<UUID> permitidas = new HashSet<>(p.outrasProfissoes());
        if (p.profissaoPrincipalId() != null) {
            permitidas.add(p.profissaoPrincipalId());
        }
        return permitidas;
    }

    private CatalogoDoCadastro.Profissao profissaoAtiva(String codigo) {
        return catalogo.profissaoAtiva(codigo)
                .orElseThrow(() ->
                        new RegraDeNegocioException("profissao-invalida", "Escolha uma das profissões da lista."));
    }

    private UUID cidadeAtiva(int codigoIbge) {
        return catalogo.cidadeAtiva(codigoIbge)
                .orElseThrow(() ->
                        new RegraDeNegocioException("cidade-invalida", "Escolha uma das cidades atendidas pela COE."));
    }

    static RegraDeNegocioException servicoInvalido() {
        return new RegraDeNegocioException(
                "servico-invalido", "Escolha serviços da lista das profissões que você marcou.");
    }

    /** 18 anos completos no dia de hoje do fuso de negócio (RN08, com Clock). */
    private void exigirIdade(LocalDate nascimento) {
        LocalDate hoje = FusoDeNegocio.hoje(clock);
        if (nascimento.isBefore(NASCIMENTO_MINIMO) || nascimento.isAfter(hoje)) {
            throw new RegraDeNegocioException("data-nascimento-invalida", "Confira a data de nascimento.");
        }
        if (nascimento.plusYears(IDADE_MINIMA).isAfter(hoje)) {
            throw new RegraDeNegocioException("idade-minima", MENSAGEM_IDADE);
        }
    }

    /**
     * 409 neutro (não diz de quem é o CPF) e auditoria numa transação própria, sem o CPF. O limite por usuário
     * das etapas segura a tentativa em massa (enumeração).
     */
    private ConflitoException cpfRepetido(UUID dono) {
        transacao.executeWithoutResult(status -> auditoria.registrar(
                dono,
                contas.de(dono).papelPrincipal(),
                "profissional.cpf-repetido",
                "usuario",
                dono,
                null,
                null,
                null));
        return new ConflitoException(
                "cpf-em-uso",
                "Não foi possível usar esse CPF neste cadastro. Se ele é seu, fale com o suporte.",
                "cpf");
    }
}
