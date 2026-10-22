package br.com.coe.servicos.profissional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/**
 * O rascunho do próprio cadastro, com quais etapas estão completas. CPF e chave Pix só mascarados; documentos
 * e fotos só por id (a chave do arquivo nunca sai da API). {@code motivoCorrecao} só com a correção pedida (o
 * texto do SMS, que já passou pela censura).
 */
record CadastroResposta(
        String status,
        int etapaAtual,
        Map<String, Boolean> etapas,
        String nome,
        String profissao,
        List<String> outrasProfissoes,
        String cpf,
        LocalDate dataNascimento,
        List<DocumentoResposta> documentos,
        List<ServicoResposta> servicos,
        Short experienciaAnos,
        String ferramentas,
        boolean mei,
        Dinheiro valorDiaria,
        Dinheiro valorMeiaDiaria,
        String horario,
        Integer cidade,
        Short raioKm,
        List<Integer> outrasCidades,
        List<Short> dias,
        List<FotoResposta> fotos,
        List<String> frases,
        String bio,
        String chavePixTipo,
        String chavePix,
        String motivoCorrecao) {

    record DocumentoResposta(UUID id, String tipo, String status) {}

    record ServicoResposta(String profissao, String nome) {}

    record FotoResposta(UUID id, String servico, String legenda) {}

    record UrlResposta(String url, Instant expiraEm) {}
}
