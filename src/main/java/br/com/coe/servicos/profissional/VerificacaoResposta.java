package br.com.coe.servicos.profissional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da verificação (DOM-03). Nunca a chave dos arquivos: documento e foto só pelo id (RN16). */
final class VerificacaoResposta {

    private VerificacaoResposta() {}

    record Fila(List<ItemDaFila> itens, boolean temMais) {}

    record ItemDaFila(UUID id, String nome, String profissao, Instant enviadoEm, boolean nr10) {}

    /**
     * O cadastro para o checklist: o CPF completo (o admin confere com o documento, RN12) e o resto como o
     * profissional vê (Pix mascarado; a chave Pix é conferida antes do primeiro repasse, RN15).
     */
    record Detalhe(
            UUID id,
            String cpf,
            boolean celularConfirmado,
            String nr10,
            Instant enviadoEm,
            CadastroResposta cadastro) {}

    record Decisao(UUID id, String status, String nr10) {}
}
