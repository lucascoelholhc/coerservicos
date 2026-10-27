import { TEMPO_DO_UPLOAD_MS } from './cadastroProfissional';
import { chamar } from './cliente';
import type { DiariaExecutada, FotosDoDia } from './tipos';

/**
 * Execução da diária (DOM-08): Cheguei, fotos e Terminei do profissional; aprovar do cliente. O backend confere quem
 * é o dono e o estado (RN40); a tela só mostra o botão certo.
 */
const BASE = '/diarias';

export function cheguei(id: string): Promise<DiariaExecutada> {
  return chamar<DiariaExecutada>(`${BASE}/${encodeURIComponent(id)}/cheguei`, { metodo: 'POST' });
}

/** Uma foto do serviço por vez (até 5 por dia). */
export async function enviarFotoDoDia(diaria: string, arquivo: File): Promise<void> {
  const corpo = new FormData();
  corpo.append('diaria', diaria);
  corpo.append('arquivo', arquivo);
  await chamar<unknown>(`${BASE}/fotos`, { metodo: 'POST', corpo, tempoLimiteMs: TEMPO_DO_UPLOAD_MS });
}

export function terminei(id: string, recado: string): Promise<DiariaExecutada> {
  const texto = recado.trim();
  return chamar<DiariaExecutada>(`${BASE}/${encodeURIComponent(id)}/terminei`, {
    metodo: 'POST',
    corpo: texto === '' ? {} : { recado: texto },
  });
}

export function fotosDoDia(id: string, sinal?: AbortSignal): Promise<FotosDoDia> {
  return chamar<FotosDoDia>(`${BASE}/${encodeURIComponent(id)}/fotos`, { sinal });
}

export function aprovarDia(id: string): Promise<DiariaExecutada> {
  return chamar<DiariaExecutada>(`${BASE}/${encodeURIComponent(id)}/aprovar`, { metodo: 'POST' });
}
