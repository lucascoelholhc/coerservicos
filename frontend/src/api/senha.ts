import { chamar } from './cliente';

export type PedidoDeNovaSenha = { token: string; novaSenha: string } | { celular: string; codigo: string; novaSenha: string };

/** POST /api/auth/senha/esqueci: resposta sempre igual (202), exista ou não a conta. */
export async function esqueciSenha(login: string): Promise<void> {
  await chamar<unknown>('/auth/senha/esqueci', { metodo: 'POST', corpo: { login } });
}

/** POST /api/auth/senha/redefinir: com o token do link ou com celular e código; não entra e derruba as sessões. */
export async function redefinirSenha(pedido: PedidoDeNovaSenha): Promise<void> {
  await chamar<unknown>('/auth/senha/redefinir', { metodo: 'POST', corpo: pedido });
}
