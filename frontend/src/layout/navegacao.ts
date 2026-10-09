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
  { rotulo: 'Início', icone: 'home', para: '/' },
  { rotulo: 'Buscar', icone: 'search', para: '/buscar' },
  { rotulo: 'Entrar', icone: 'user', para: '/entrar' },
];

/** Menu de cima (a partir de 900 px): sem "Entrar" (vira botão) e com as seções do Início. */
export const MENU_TOPO_VISITANTE: readonly ItemDeMenu[] = [
  { rotulo: 'Início', icone: 'home', para: '/' },
  { rotulo: 'Buscar', icone: 'search', para: '/buscar' },
  { rotulo: 'Como funciona', icone: 'help', ancora: '/#como-funciona' },
  { rotulo: 'Para profissionais', icone: 'hand', ancora: '/#para-profissionais' },
];
