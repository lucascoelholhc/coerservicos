import { chamar } from './cliente';
import type { EnvioDaMensagem, MensagemDoChat, ResumoDaConversa } from './tipos';

/** Chat (DOM-05, FE-05): antes do pagamento, a censura no servidor bloqueia telefone, e-mail e redes. */
export function abrirConversa(profissionalId: string): Promise<ResumoDaConversa> {
  return chamar<ResumoDaConversa>('/conversas', { metodo: 'POST', corpo: { profissionalId } });
}

export function listarConversas(sinal: AbortSignal): Promise<{ itens: ResumoDaConversa[] }> {
  return chamar<{ itens: ResumoDaConversa[] }>('/conversas', { sinal });
}

/** As 100 mensagens mais recentes, em ordem de envio. */
export function buscarMensagens(conversa: string, sinal: AbortSignal): Promise<{ itens: MensagemDoChat[] }> {
  return chamar<{ itens: MensagemDoChat[] }>(`/conversas/${encodeURIComponent(conversa)}/mensagens`, { sinal });
}

export function enviarMensagem(conversa: string, texto: string): Promise<EnvioDaMensagem> {
  return chamar<EnvioDaMensagem>(`/conversas/${encodeURIComponent(conversa)}/mensagens`, {
    metodo: 'POST',
    corpo: { texto },
  });
}

export function marcarLidas(conversa: string): Promise<void> {
  return chamar<void>(`/conversas/${encodeURIComponent(conversa)}/lidas`, { metodo: 'POST' });
}
