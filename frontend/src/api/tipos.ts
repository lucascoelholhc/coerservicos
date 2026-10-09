/*
 * Tipos das respostas da API, escritos à mão iguais aos DTOs do backend (OpenAPI fica para depois).
 */

/** GET /api/publico/catalogo (CatalogoResposta). */
export interface Catalogo {
  areas: AreaDoCatalogo[];
  cidades: CidadeDoCatalogo[];
}

export interface AreaDoCatalogo {
  codigo: string;
  nome: string;
  profissoes: ProfissaoDoCatalogo[];
}

/** O front escolhe a ilustração pelo {@link codigo}, não pelo {@link icone}. */
export interface ProfissaoDoCatalogo {
  codigo: string;
  nome: string;
  nomePlural: string;
  icone: string;
  servicos: { nome: string }[];
}

export interface CidadeDoCatalogo {
  codigoIbge: number;
  nome: string;
  uf: string;
}

export type TaxaPagaPor = 'CLIENTE' | 'PROFISSIONAL';

/** GET /api/publico/regras: comissão como fração ("0.1000") e prazo em ISO-8601 ("PT12H"). */
export interface Regras {
  comissao: string;
  prazoLiberacao: string;
  taxaPagaPor: TaxaPagaPor;
}

/** Resposta do entrar e do renovar (SessaoResponse). */
export interface SessaoResposta {
  accessToken: string;
  expiraEm: string;
  usuario: UsuarioDaSessao;
}

export interface UsuarioDaSessao {
  id: string;
  nome: string;
  papeis: string[];
  contatoPendente: boolean;
}
