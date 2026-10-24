package br.com.coe.servicos.profissional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * Respostas públicas da busca e do perfil (DOM-04). Nunca contato nem dado pessoal (RN22, PA13): sem celular,
 * e-mail, CPF, Pix, endereço, nascimento ou documento; o nome sai como "Primeiro S.". O id é o do perfil do
 * profissional (nunca o da conta).
 */
final class BuscaResposta {

    private BuscaResposta() {}

    record Cidade(int codigoIbge, String nome, String uf) {}

    record Busca(List<Item> itens, boolean temMais) {}

    /** {@code distanciaKm} só quando a busca tem cidade: da cidade base do profissional até ela. */
    record Item(
            UUID id,
            String nome,
            String profissao,
            List<String> outrasProfissoes,
            Dinheiro valorDiaria,
            Dinheiro valorMeiaDiaria,
            int experienciaAnos,
            Cidade cidade,
            BigDecimal distanciaKm,
            LocalDate proximoDiaLivre,
            boolean seloNr10,
            boolean novoNaCoe) {}

    record Servico(String profissao, String nome) {}

    /** Foto do portfólio com URL assinada curta (o bucket é privado). */
    record Foto(UUID id, String servico, String legenda, String url, Instant expiraEm) {}

    /** {@code nota} só existe com avaliações (RN23: nunca nota inventada); sem elas, "Novo na COE". */
    record Avaliacoes(int total, BigDecimal nota, boolean novoNaCoe) {}

    record Perfil(
            UUID id,
            String nome,
            String profissao,
            List<String> outrasProfissoes,
            List<Servico> servicos,
            int experienciaAnos,
            String ferramentas,
            String horario,
            Dinheiro valorDiaria,
            Dinheiro valorMeiaDiaria,
            Cidade cidade,
            int raioKm,
            List<Cidade> outrasCidades,
            List<Integer> diasDaSemana,
            List<LocalDate> diasLivres,
            String sobre,
            boolean seloNr10,
            Avaliacoes avaliacoes,
            List<Foto> fotos) {}
}
