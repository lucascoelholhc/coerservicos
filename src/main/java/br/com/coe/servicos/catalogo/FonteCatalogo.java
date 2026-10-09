package br.com.coe.servicos.catalogo;

/** Lê o catálogo ativo direto do banco (sem cache; quem guarda é o {@link CatalogoPublico}). */
interface FonteCatalogo {

    CatalogoResposta ler();
}
