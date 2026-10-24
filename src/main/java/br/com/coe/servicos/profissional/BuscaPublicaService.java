package br.com.coe.servicos.profissional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.coe.servicos.compartilhado.FusoDeNegocio;
import br.com.coe.servicos.compartilhado.armazenamento.Armazenamento;
import br.com.coe.servicos.compartilhado.armazenamento.PropriedadesDoArmazenamento;
import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.erro.ServicoInstavelException;
import br.com.coe.servicos.compartilhado.pessoal.NomeDeExibicao;

/**
 * Busca e perfil público (DOM-04, RF08, RF09, RF12, RN18 a RN23), sem login. Dias livres a partir de amanhã (no
 * fuso de negócio), em quatro semanas; o filtro de dia aceita até 90 dias. O nome sai como "Primeiro S." (sem
 * sobrenome completo, menos dado pessoal num endpoint público). Fotos com URL assinada curta, nunca documento ou
 * selfie, nem foto ocultada pelo admin (RN16). Cada consulta tem teto de tempo
 * ({@code coe.busca.tempo-maximo}); passou = 503.
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
    /** Maior código de profissão ou nome de serviço do catálogo, com folga. */
    static final int TEXTO_MAXIMO = 80;

    private final BuscaDeProfissionais consultas;
    private final Armazenamento armazenamento;
    private final Clock clock;
    private final Duration validadeDaFoto;
    private final Duration tempoMaximo;

    BuscaPublicaService(
            BuscaDeProfissionais consultas,
            Armazenamento armazenamento,
            Clock clock,
            PropriedadesDoArmazenamento propriedades,
            @Value("${coe.busca.tempo-maximo:2s}") Duration tempoMaximo) {
        this.consultas = consultas;
        this.armazenamento = armazenamento;
        this.clock = clock;
        this.validadeDaFoto = propriedades.validadeUrl();
        this.tempoMaximo = tempoMaximo;
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
            String ferramentas,
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
        exigirFaixa(pedido);
        int pagina = Math.clamp(pedido.pagina(), 0, PAGINA_MAXIMA);
        BuscaDeProfissionais.Filtro filtro = new BuscaDeProfissionais.Filtro(
                textoOuNulo(pedido.profissao()),
                textoOuNulo(pedido.servico()),
                pedido.cidade(),
                pedido.dia(),
                valorMaximo(pedido.valorMaximo()),
                pedido.experienciaMinima(),
                pedido.meiaDiaria(),
                ferramentas(pedido.ferramentas()),
                ordem(pedido.ordem()),
                // um a mais para saber se há outra página
                POR_PAGINA + 1,
                (long) pagina * POR_PAGINA,
                amanha,
                amanha.plusDays(JANELA_DE_DIAS - 1L));
        List<BuscaDeProfissionais.Linha> linhas;
        try {
            consultas.limitarTempo(tempoMaximo);
            linhas = consultas.buscar(filtro);
        } catch (QueryTimeoutException demorou) {
            throw new ServicoInstavelException("A busca demorou demais. Tente de novo em instantes.", demorou);
        }
        List<BuscaResposta.Item> itens = linhas.stream()
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
        consultas.limitarTempo(tempoMaximo);
        LocalDate amanha = FusoDeNegocio.hoje(clock).plusDays(1);
        List<BuscaResposta.Foto> fotos = consultas.fotos(id, FOTOS_NO_PERFIL).stream()
                .map(foto -> {
                    // expiraEm é o limite de cima: o instante é tomado antes de assinar
                    Instant expira = clock.instant().plus(validadeDaFoto);
                    URI url = armazenamento.urlAssinada(foto.chave(), validadeDaFoto);
                    return new BuscaResposta.Foto(foto.id(), foto.servico(), foto.legenda(), url.toString(), expira);
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
                textoDoSobre(dados.frases(), dados.bio()),
                dados.selo(),
                avaliacoes(dados.totalAvaliacoes(), dados.notaMedia()),
                fotos);
    }

    /** "Valdir Schmitt" vira "Valdir S."; um nome só fica como está. */
    static String nomeDeExibicao(String nome) {
        return NomeDeExibicao.de(nome);
    }

    /**
     * O "Sobre você" como a prévia do cadastro mostra (FE-04): cada frase pronta com ponto final, depois o texto
     * livre (tudo já passado pela censura).
     */
    static String textoDoSobre(List<String> frases, String bio) {
        StringBuilder texto = new StringBuilder();
        for (String frase : frases) {
            String limpa = frase.strip();
            texto.append(limpa);
            if (!limpa.endsWith(".") && !limpa.endsWith("!") && !limpa.endsWith("?")) {
                texto.append('.');
            }
            texto.append(' ');
        }
        if (bio != null) {
            texto.append(bio.strip());
        }
        return texto.toString().strip();
    }

    /** RN23: sem avaliações, "Novo na COE" e nota nula, nunca uma nota inventada. */
    private static BuscaResposta.Avaliacoes avaliacoes(int total, BigDecimal nota) {
        return total == 0 || nota == null
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

    /** Texto de filtro com tamanho limitado e números não negativos (pedido público, nunca confiar no front). */
    private static void exigirFaixa(Pedido pedido) {
        if (longo(pedido.profissao()) || longo(pedido.servico())) {
            throw new RegraDeNegocioException("filtro-invalido", "Use um filtro mais curto.");
        }
        if (pedido.experienciaMinima() != null && pedido.experienciaMinima() < 0) {
            throw new RegraDeNegocioException("filtro-invalido", "A experiência mínima não pode ser negativa.");
        }
    }

    private static boolean longo(String texto) {
        return texto != null && texto.length() > TEXTO_MAXIMO;
    }

    /** "sim" = traz todas as ferramentas; "parte" = traz ao menos parte. */
    private static String ferramentas(String texto) {
        String valor = textoOuNulo(texto);
        if (valor == null || valor.equals("sim") || valor.equals("parte")) {
            return valor;
        }
        throw new RegraDeNegocioException("filtro-invalido", "Filtre as ferramentas por \"sim\" ou \"parte\".");
    }

    private static Dinheiro valorMaximo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            Dinheiro valor = Dinheiro.de(texto.strip());
            if (valor.compareTo(Dinheiro.ZERO) <= 0) {
                throw new IllegalArgumentException("valor máximo sem sentido");
            }
            return valor;
        } catch (RuntimeException invalido) {
            throw new RegraDeNegocioException("valor-invalido", "Escreva o valor máximo assim: 250.00.");
        }
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
