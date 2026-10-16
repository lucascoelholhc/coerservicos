package br.com.coe.servicos.profissional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.log.Mascara;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/** Etapas completas, o que falta para enviar e a resposta mascarada do cadastro. */
@Component
class MontadorDoCadastro {

    /** As 11 etapas na ordem do protótipo, com o nome que aparece em "Falta completar". */
    private static final Map<String, String> ETAPAS = etapas();

    private final CatalogoDoCadastro catalogo;
    private final ContaDoCadastro contas;
    private final DocumentoVerificacaoRepository documentos;
    private final FotoPortfolioRepository fotos;
    private final ConfiguracaoNegocio configuracao;

    MontadorDoCadastro(
            CatalogoDoCadastro catalogo,
            ContaDoCadastro contas,
            DocumentoVerificacaoRepository documentos,
            FotoPortfolioRepository fotos,
            ConfiguracaoNegocio configuracao) {
        this.catalogo = catalogo;
        this.contas = contas;
        this.documentos = documentos;
        this.fotos = fotos;
        this.configuracao = configuracao;
    }

    private static Map<String, String> etapas() {
        Map<String, String> etapas = new LinkedHashMap<>();
        etapas.put("trabalho", "o trabalho");
        etapas.put("dados", "seus dados");
        etapas.put("documentos", "documento e selfie");
        etapas.put("servicos", "o que você faz");
        etapas.put("experiencia", "experiência");
        etapas.put("valor", "valor por dia");
        etapas.put("onde", "onde trabalha");
        etapas.put("dias", "dias de trabalho");
        etapas.put("fotos", "fotos de trabalhos");
        etapas.put("sobre", "sobre você");
        etapas.put("pix", "chave Pix");
        return etapas;
    }

    Map<String, Boolean> completas(Profissional p) {
        List<String> tipos = documentos.findByProfissionalId(p.id()).stream()
                .map(DocumentoVerificacao::tipo)
                .toList();
        Map<String, Boolean> completas = new LinkedHashMap<>();
        completas.put("trabalho", p.profissaoPrincipalId() != null);
        completas.put("dados", p.cpf() != null && p.dataNascimento() != null);
        // RG (frente; o verso é opcional) ou CNH, e a selfie (RN08)
        completas.put(
                "documentos",
                (tipos.contains(TipoDeDocumento.RG_FRENTE.codigo()) || tipos.contains(TipoDeDocumento.CNH.codigo()))
                        && tipos.contains(TipoDeDocumento.SELFIE.codigo()));
        completas.put("servicos", !p.servicos().isEmpty());
        completas.put("experiencia", p.experienciaAnos() != null && p.ferramentas() != null);
        completas.put("valor", p.valorDiaria() != null);
        completas.put("onde", p.cidadeBaseId() != null && p.raioKm() != null);
        completas.put("dias", !p.dias().isEmpty());
        completas.put("fotos", fotos.countByProfissionalId(p.id()) > 0);
        completas.put("sobre", p.textoSobre().length() >= configuracao.sobreMinimoCaracteres());
        completas.put("pix", p.chavePixTipo() != null && p.chavePix() != null);
        return completas;
    }

    /** Primeira etapa incompleta (1 a 11); tudo completo = 11. */
    int etapaAtual(Map<String, Boolean> completas) {
        int etapa = 1;
        for (boolean completa : completas.values()) {
            if (!completa) {
                return etapa;
            }
            etapa++;
        }
        return ETAPAS.size();
    }

    /** Nomes das etapas que faltam, para a mensagem do envio. */
    List<String> faltas(Map<String, Boolean> completas) {
        List<String> faltas = new ArrayList<>();
        completas.forEach((etapa, completa) -> {
            if (!completa) {
                faltas.add(ETAPAS.get(etapa));
            }
        });
        return faltas;
    }

    CadastroResposta montar(Profissional p, UUID usuario) {
        Map<String, Boolean> completas = completas(p);
        return new CadastroResposta(
                p.status(),
                etapaAtual(completas),
                completas,
                contas.de(usuario).nome(),
                Optional.ofNullable(p.profissaoPrincipalId())
                        .flatMap(catalogo::profissao)
                        .map(CatalogoDoCadastro.Profissao::codigo)
                        .orElse(null),
                p.outrasProfissoes().stream()
                        .map(catalogo::profissao)
                        .flatMap(Optional::stream)
                        .map(CatalogoDoCadastro.Profissao::codigo)
                        .sorted()
                        .toList(),
                p.cpf() == null ? null : Mascara.cpf(p.cpf().digitos()),
                p.dataNascimento(),
                documentos.findByProfissionalId(p.id()).stream()
                        .map(d -> new CadastroResposta.DocumentoResposta(d.getId(), d.tipo(), d.status()))
                        .toList(),
                p.servicos().stream()
                        .map(catalogo::servico)
                        .flatMap(Optional::stream)
                        .map(s -> new CadastroResposta.ServicoResposta(s.profissao(), s.nome()))
                        .sorted(Comparator.comparing(CadastroResposta.ServicoResposta::nome))
                        .toList(),
                p.experienciaAnos(),
                p.ferramentas(),
                p.mei(),
                p.valorDiaria(),
                p.valorMeiaDiaria(),
                p.horario(),
                Optional.ofNullable(p.cidadeBaseId())
                        .flatMap(catalogo::codigoIbge)
                        .orElse(null),
                p.raioKm(),
                p.outrasCidades().stream()
                        .map(catalogo::codigoIbge)
                        .flatMap(Optional::stream)
                        .sorted()
                        .toList(),
                p.dias().stream().sorted().toList(),
                fotos.findByProfissionalIdOrderByOrdemAsc(p.id()).stream()
                        .map(f -> new CadastroResposta.FotoResposta(f.getId(), nomeDoServico(f), f.legenda()))
                        .toList(),
                p.frases(),
                p.bio(),
                p.chavePixTipo(),
                ChavesPix.mascarar(p.chavePixTipo(), p.chavePix()));
    }

    String nomeDoServico(FotoPortfolio foto) {
        return Optional.ofNullable(foto.servicoId())
                .flatMap(catalogo::servico)
                .map(CatalogoDoCadastro.Servico::nome)
                .orElse(null);
    }
}
