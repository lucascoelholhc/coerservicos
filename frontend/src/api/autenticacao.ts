import { chamar } from './cliente';
import type { SessaoResposta } from './tipos';

/** POST /api/auth/entrar (login = celular com ou sem máscara, ou e-mail). 403 segundo-passo-necessario traz desafioId. */
export function entrar(dados: { login: string; senha: string }): Promise<SessaoResposta> {
  return chamar<SessaoResposta>('/auth/entrar', { metodo: 'POST', corpo: dados });
}

/** POST /api/auth/segundo-passo: o código por SMS do MFA (ou do ADMIN). */
export function segundoPasso(dados: { desafioId: string; codigo: string }): Promise<SessaoResposta> {
  return chamar<SessaoResposta>('/auth/segundo-passo', { metodo: 'POST', corpo: dados });
}

/** POST /api/auth/sair: encerra a sessão deste aparelho (o cookie do refresh vai junto). */
export async function sair(): Promise<void> {
  await chamar<unknown>('/auth/sair', { metodo: 'POST' });
}

/** POST /api/auth/sair-de-todos: encerra a sessão em todos os aparelhos. */
export async function sairDeTodos(): Promise<void> {
  await chamar<unknown>('/auth/sair-de-todos', { metodo: 'POST' });
}
