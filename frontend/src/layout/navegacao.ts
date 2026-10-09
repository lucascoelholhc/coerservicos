import { CAMINHOS, SECOES_DO_INICIO } from '../caminhos';
import type { NomeDoIcone } from '../icones/Icone';

export interface ItemDeMenu {
  rotulo: string;
  icone: NomeDoIcone;
  /** Rota do React Router (NavLink, marca a página atual). */
  para?: string;
  /** Seção do Início (link comum com âncora). */
  ancora?: string;
}

/** Menu de baixo (celular) do visitante, como no protótipo (NAV, ef7d397). */
export const MENU_VISITANTE: readonly (ItemDeMenu & { para: string })[] = [
  { rotulo: 'Início', icone: 'home', para: CAMINHOS.inicio },
  { rotulo: 'Buscar', icone: 'search', para: CAMINHOS.busca },
  { rotulo: 'Entrar', icone: 'user', para: CAMINHOS.entrar },
];

/** Menu de baixo de quem entrou: "Minha conta" no lugar de "Entrar". */
export const MENU_LOGADO: readonly (ItemDeMenu & { para: string })[] = [
  { rotulo: 'Início', icone: 'home', para: CAMINHOS.inicio },
  { rotulo: 'Buscar', icone: 'search', para: CAMINHOS.busca },
  { rotulo: 'Minha conta', icone: 'user', para: CAMINHOS.conta },
];

/** Menu de cima (a partir de 900 px): sem "Entrar" (vira botão) e com as seções do Início. */
export const MENU_TOPO_VISITANTE: readonly ItemDeMenu[] = [
  { rotulo: 'Início', icone: 'home', para: CAMINHOS.inicio },
  { rotulo: 'Buscar', icone: 'search', para: CAMINHOS.busca },
  { rotulo: 'Como funciona', icone: 'help', ancora: SECOES_DO_INICIO.comoFunciona },
  { rotulo: 'Para profissionais', icone: 'hand', ancora: SECOES_DO_INICIO.paraProfissionais },
];
