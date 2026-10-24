/** Rotas do front num lugar só: menu, herói, rodapé, páginas e testes usam estas constantes. */
export const CAMINHOS = {
  inicio: '/',
  busca: '/busca',
  perfil: '/profissionais',
  entrar: '/entrar',
  criarConta: '/criar-conta',
  criarContaCliente: '/criar-conta/cliente',
  criarContaProfissional: '/criar-conta/profissional',
  cadastro: '/cadastro',
  adminVerificacao: '/admin/verificacao',
  conta: '/conta',
  confirmarCelular: '/conta/confirmar-celular',
  codigoNoLogin: '/conta/codigo-no-login',
  trocarSenha: '/conta/trocar-senha',
  recolocarContato: '/conta/recolocar-contato',
  esqueciSenha: '/esqueci-senha',
  redefinirSenha: '/redefinir-senha',
  confirmarEmail: '/confirmar-email',
  provarEmail: '/provar-email',
  termos: '/termos',
  privacidade: '/privacidade',
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

/** Detalhe de um cadastro na verificação do admin. */
export function caminhoDaVerificacao(id: string): string {
  return `${CAMINHOS.adminVerificacao}/${encodeURIComponent(id)}`;
}

/** Perfil público de um profissional (DOM-04); o id é o do perfil, nunca o da conta. */
export function caminhoDoPerfil(id: string): string {
  return `${CAMINHOS.perfil}/${encodeURIComponent(id)}`;
}
