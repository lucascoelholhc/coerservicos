package br.com.coe.servicos.profissional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.armazenamento.Armazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.PropriedadesDoArmazenamento;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Busca e perfil público (DOM-04, RF08, RF09, RF12, RN18 a RN23), sem login. Dias livres a partir de amanhã (no
 * fuso de negócio), em quatro semanas; o filtro de dia aceita até 90 dias. O nome sai como "Primeiro S." (sem
 * sobrenome completo, menos dado pessoal num endpoint público). Fotos com URL assinada curta, nunca documento ou
 * selfie (RN16).
 */
@Service
class BuscaPublicaService {

    static final int POR_PAGINA = 20;
    /** Teto da página (OFFSET grande custa caro; ninguém folheia 50 páginas). */
    static final int PAGINA_MAXIMA = 50;
    /** Dias livres das próximas quatro semanas (perfil e "livre antes"). */
    static final int JANELA_DE_DIAS = 28;

    static final int DIA_MAXIMO = 90;
    static final int FOTOS_NO_PERFIL = 12;

    private final BuscaDeProfissionais consultas;
    private final Armazenamento armazenamento;
    private final Clock clock;
    private final Duration validadeDaFoto;

    BuscaPublicaService(
            BuscaDeProfissionais consultas,
            Armazenamento armazenamento,
            Clock clock,
            PropriedadesDoArmazenamento propriedades) {
        this.consultas = consultas;
        this.armazenamento = armazenamento;
        this.clock = clock;
        this.validadeDaFoto = propriedades.validadeUrl();
    }

    /** O pedido como veio na URL (texto ou nulo); a validação fica aqui. */
    record Pedido(
            String profissao,
            String servico,
            Integer cidade,
            LocalDate dia,
            String valorMaximo,
            Integer experienciaMinima,
            boolean meiaDiaria,
            String ordem,
            int pagina) {}

    @Transactional(readOnly = true)
    BuscaResposta.Busca buscar(Pedido pedido) {
        LocalDate amanha = FusoDeNegocio.hoje(clock).plusDays(1);
        if (pedido.dia() != null
                && (pedido.dia().isBefore(amanha) || pedido.dia().isAfter(amanha.plusDays(DIA_MAXIMO - 1L)))) {
            throw new RegraDeNegocioException(
                    "dia-invalido", "Escolha um dia a partir de amanhã, até " + DIA_MAXIMO + " dias para a frente.");
        }
        int pagina = Math.clamp(pedido.pagina(), 0, PAGINA_MAXIMA);
        BuscaDeProfissionais.Filtro filtro = new BuscaDeProfissionais.Filtro(
                textoOuNulo(pedido.profissao()),
                textoOuNulo(pedido.servico()),
                pedido.cidade(),
                pedido.dia(),
                valorMaximo(pedido.valorMaximo()),
                pedido.experienciaMinima(),
                pedido.meiaDiaria(),
                ordem(pedido.ordem()),
                // um a mais para saber se há outra página
                POR_PAGINA + 1,
                (long) pagina * POR_PAGINA,
                amanha,
                amanha.plusDays(JANELA_DE_DIAS - 1L));
        List<BuscaResposta.Item> itens = consultas.buscar(filtro).stream()
                .map(linha -> new BuscaResposta.Item(
                        linha.id(),
                        nomeDeExibicao(linha.nome()),
                        linha.profissao(),
                        linha.outras(),
                        linha.valorDiaria(),
                        linha.valorMeiaDiaria(),
                        linha.experienciaAnos(),
                        linha.cidade(),
                        linha.distanciaKm(),
                        linha.proximoLivre(),
                        linha.selo(),
                        linha.totalAvaliacoes() == 0))
                .toList();
        boolean temMais = itens.size() > POR_PAGINA;
        return new BuscaResposta.Busca(temMais ? itens.subList(0, POR_PAGINA) : itens, temMais);
    }

    @Transactional(readOnly = true)
    BuscaResposta.Perfil perfil(UUID id) {
        BuscaDeProfissionais.Dados dados = consultas
                .dados(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrado."));
        LocalDate amanha = FusoDeNegocio.hoje(clock).plusDays(1);
        List<BuscaResposta.Foto> fotos = consultas.fotos(id, FOTOS_NO_PERFIL).stream()
                .map(foto -> {
                    URI url = armazenamento.urlAssinada(foto.chave(), validadeDaFoto);
                    return new BuscaResposta.Foto(
                            foto.id(),
                            foto.servico(),
                            foto.legenda(),
                            url.toString(),
                            clock.instant().plus(validadeDaFoto));
                })
                .toList();
        return new BuscaResposta.Perfil(
                dados.id(),
                nomeDeExibicao(dados.nome()),
                dados.profissao(),
                dados.outras(),
                consultas.servicos(id),
                dados.experienciaAnos(),
                dados.ferramentas(),
                dados.horario(),
                dados.valorDiaria(),
                dados.valorMeiaDiaria(),
                dados.cidade(),
                dados.raioKm(),
                consultas.outrasCidades(id),
                consultas.diasDaSemana(id),
                consultas.diasLivres(id, amanha, amanha.plusDays(JANELA_DE_DIAS - 1L)),
                sobre(dados.frases(), dados.bio()),
                dados.selo(),
                avaliacoes(dados.totalAvaliacoes(), dados.notaMedia()),
                fotos);
    }

    /** "Valdir Schmitt" vira "Valdir S."; um nome só fica como está. */
    static String nomeDeExibicao(String nome) {
        String[] partes = nome.strip().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        String ultimo = partes[partes.length - 1];
        return partes[0] + " " + ultimo.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    /** O mesmo texto do "Sobre você" do cadastro (frases prontas + texto livre, já passados pela censura). */
    private static String sobre(List<String> frases, String bio) {
        String juntas = String.join(" ", frases);
        String texto = bio == null ? "" : bio.strip();
        return (juntas + " " + texto).strip();
    }

    /** RN23: sem avaliações, "Novo na COE" e nota nula, nunca uma nota inventada. */
    private static BuscaResposta.Avaliacoes avaliacoes(int total, BigDecimal nota) {
        return total == 0
                ? new BuscaResposta.Avaliacoes(0, null, true)
                : new BuscaResposta.Avaliacoes(total, nota, false);
    }

    private static BuscaDeProfissionais.Ordem ordem(String ordem) {
        if (ordem == null || ordem.isBlank()) {
            return BuscaDeProfissionais.Ordem.LIVRE;
        }
        return switch (ordem.strip()) {
            case "livre" -> BuscaDeProfissionais.Ordem.LIVRE;
            case "preco" -> BuscaDeProfissionais.Ordem.PRECO;
            case "experiencia" -> BuscaDeProfissionais.Ordem.EXPERIENCIA;
            default ->
                throw new RegraDeNegocioException(
                        "ordem-invalida", "Ordene por quem está livre antes, menor preço ou experiência.");
        };
    }

    private static Dinheiro valorMaximo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Dinheiro.de(texto.strip());
        } catch (RuntimeException invalido) {
            throw new RegraDeNegocioException("valor-invalido", "Escreva o valor máximo assim: 250.00.");
        }
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
