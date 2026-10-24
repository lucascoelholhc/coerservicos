package br.com.coe.servicos.profissional;

import br.com.coe.servicos.compartilhado.armazenamento.Categoria;

/** Documentos do cadastro (etapa 3). O comprovante de MEI fica para depois (não é exigido no RN08). */
enum TipoDeDocumento {
    RG_FRENTE("rg_frente", Categoria.DOCUMENTO),
    RG_VERSO("rg_verso", Categoria.DOCUMENTO),
    CNH("cnh", Categoria.DOCUMENTO),
    SELFIE("selfie", Categoria.SELFIE),
    CERTIFICADO_NR10("certificado_nr10", Categoria.DOCUMENTO);

    static final String CODIGOS = "rg_frente|rg_verso|cnh|selfie|certificado_nr10";

    private final String codigo;
    private final Categoria categoria;

    TipoDeDocumento(String codigo, Categoria categoria) {
        this.codigo = codigo;
        this.categoria = categoria;
    }

    static TipoDeDocumento deCodigo(String codigo) {
        for (TipoDeDocumento tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de documento desconhecido");
    }

    String codigo() {
        return codigo;
    }

    Categoria categoria() {
        return categoria;
    }
}
