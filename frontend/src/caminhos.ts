/** Rotas do front num lugar só: menu, herói, rodapé, páginas e testes usam estas constantes. */
export const CAMINHOS = {
  inicio: '/',
  busca: '/busca',
  entrar: '/entrar',
  criarConta: '/criar-conta',
  comoFunciona: '/como-funciona',
  paraProfissionais: '/para-profissionais',
} as const;

/** Seções do Início abertas por âncora (o Router rola até elas, sem recarregar). */
export const SECOES_DO_INICIO = {
  categorias: '/#categorias',
  comoFunciona: '/#como-funciona',
  paraProfissionais: '/#para-profissionais',
} as const;

/** /busca com os filtros escolhidos: código da profissão e código IBGE da cidade (vazios ficam de fora). */
export function caminhoDaBusca(filtros: { profissao?: string; cidade?: string }): string {
  const consulta = new URLSearchParams();
  if (filtros.profissao) {
    consulta.set('profissao', filtros.profissao);
  }
  if (filtros.cidade) {
    consulta.set('cidade', filtros.cidade);
  }
  const texto = consulta.toString();
  return texto === '' ? CAMINHOS.busca : `${CAMINHOS.busca}?${texto}`;
}
