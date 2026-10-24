package br.com.coe.servicos.config;

import java.util.Set;

/**
 * Rotas que recebem foto por multipart (CORE-09): só nelas o corpo pode passar dos 64 KB, até
 * {@code coe.limites.corpo-maximo-upload}. Vazia até os endpoints de upload (DOM-02 documentos e selfie,
 * DOM-04 portfólio, DOM-08 evidências); rota nova de upload = uma linha aqui e um limite por usuário.
 */
final class RotasDeUpload {

    static final Set<String> CAMINHOS =
            Set.of("/api/profissional/cadastro/documentos", "/api/profissional/cadastro/fotos", "/api/diarias/fotos");

    private RotasDeUpload() {}
}
