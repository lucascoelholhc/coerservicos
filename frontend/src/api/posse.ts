import { chamar } from './cliente';
import { ErroDaApi } from './erros';
import { mensagemDaResposta } from './mensagem';

/*
 * Prova de posse (RN61): quem prova que o celular ou o e-mail é seu recebe um comprovante (30 min)
 * para o cadastro. O comprovante fica só na memória da tela: nunca em URL, localStorage ou
 * sessionStorage.
 */

const ENVIADO = 'Se este contato estiver em outra conta sem confirmação, enviamos a confirmação.';

export interface PosseDoEmail {
  comprovante: string;
  email: string;
}

function textoPreenchido(valor: unknown): valor is string {
  return typeof valor === 'string' && valor !== '';
}

/** POST /api/contas/posse/celular (202, a mesma resposta para qualquer celular). */
export async function pedirPosseDoCelular(celular: string): Promise<string> {
  return mensagemDaResposta(await chamar<unknown>('/contas/posse/celular', { metodo: 'POST', corpo: { celular } }), ENVIADO);
}

/** POST /api/contas/posse/email (202): o link vai para /provar-email#token=. */
export async function pedirPosseDoEmail(email: string): Promise<string> {
  return mensagemDaResposta(await chamar<unknown>('/contas/posse/email', { metodo: 'POST', corpo: { email } }), ENVIADO);
}

/** POST /api/contas/posse/celular/confirmar: o comprovante do celular (422 codigo-invalido). */
export async function confirmarPosseDoCelular(dados: { celular: string; codigo: string }): Promise<string> {
  const resposta = await chamar<unknown>('/contas/posse/celular/confirmar', { metodo: 'POST', corpo: dados });
  const comprovante = (resposta as { comprovante?: unknown } | null)?.comprovante;
  if (!textoPreenchido(comprovante)) {
    throw new ErroDaApi('resposta');
  }
  return comprovante;
}

/** POST /api/contas/posse/email/confirmar: o comprovante e o e-mail (normalizado) do link (422 link-invalido). */
export async function confirmarPosseDoEmail(token: string): Promise<PosseDoEmail> {
  const resposta = await chamar<unknown>('/contas/posse/email/confirmar', { metodo: 'POST', corpo: { token } });
  const { comprovante, email } = (resposta ?? {}) as Record<string, unknown>;
  if (!textoPreenchido(comprovante) || !textoPreenchido(email)) {
    throw new ErroDaApi('resposta');
  }
  return { comprovante, email };
}
