import { chamar } from './cliente';
import { mensagemDaResposta } from './mensagem';
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

const CODIGO_ENVIADO = 'Enviamos um código por SMS para o seu celular.';

/** POST /api/contas/eu/celular/codigo (202; 409 celular-ja-confirmado; 429). */
export async function pedirCodigoDoCelular(): Promise<string> {
  return mensagemDaResposta(await chamar<unknown>('/contas/eu/celular/codigo', { metodo: 'POST' }), CODIGO_ENVIADO);
}

/** POST /api/contas/eu/celular/confirmar (204; 422 codigo-invalido). */
export async function confirmarCelular(codigo: string): Promise<void> {
  await chamar<unknown>('/contas/eu/celular/confirmar', { metodo: 'POST', corpo: { codigo } });
}

/** POST /api/contas/eu/mfa/codigo (202; 403 celular-nao-confirmado; 429). */
export async function pedirCodigoDoMfa(): Promise<string> {
  return mensagemDaResposta(await chamar<unknown>('/contas/eu/mfa/codigo', { metodo: 'POST' }), CODIGO_ENVIADO);
}

/** POST /api/contas/eu/mfa: liga ou desliga o código no login (204; 403 mfa-obrigatorio para ADMIN). */
export async function definirMfa(dados: { ativo: boolean; codigo: string }): Promise<void> {
  await chamar<unknown>('/contas/eu/mfa', { metodo: 'POST', corpo: dados });
}
