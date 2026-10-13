import { chamar } from './cliente';
import type { MinhaConta } from './tipos';

export interface NovoCliente {
  nome: string;
  celular: string;
  email: string;
  cep: string;
  senha: string;
  versaoTermosAceita: string;
}

/** POST /api/contas/cliente: cria a conta (não faz login; quem chama entra em seguida). */
export function criarCliente(dados: NovoCliente): Promise<{ id: string; nome: string }> {
  return chamar<{ id: string; nome: string }>('/contas/cliente', { metodo: 'POST', corpo: dados });
}

/** GET /api/contas/eu: os dados da própria conta (o dono vem do token). */
export function buscarMinhaConta(sinal: AbortSignal): Promise<MinhaConta> {
  return chamar<MinhaConta>('/contas/eu', { sinal });
}

/** POST /api/contas/eu/email/confirmacao: manda o link de confirmação de novo (202). */
export async function reenviarConfirmacaoDeEmail(): Promise<void> {
  await chamar<unknown>('/contas/eu/email/confirmacao', { metodo: 'POST' });
}

/** POST /api/contas/email/confirmar: o token do link vai no corpo (204 ou 422 link-invalido). */
export async function confirmarEmail(token: string): Promise<void> {
  await chamar<unknown>('/contas/email/confirmar', { metodo: 'POST', corpo: { token } });
}
