import { chamar } from './cliente';
import type { AgendaDoProfissional, DiariaDoProfissional, DinheiroDoProfissional } from './tipos';

/** Área do profissional (FE-06). O perfil vem do token; os valores (repasse, saldo) vêm prontos da API. */
const BASE = '/profissional';

export function listarMinhasDiarias(sinal: AbortSignal): Promise<{ itens: DiariaDoProfissional[] }> {
  return chamar<{ itens: DiariaDoProfissional[] }>(`${BASE}/diarias`, { sinal });
}

export function buscarDinheiro(sinal: AbortSignal): Promise<DinheiroDoProfissional> {
  return chamar<DinheiroDoProfissional>(`${BASE}/dinheiro`, { sinal });
}

export function buscarAgenda(sinal: AbortSignal): Promise<AgendaDoProfissional> {
  return chamar<AgendaDoProfissional>(`${BASE}/agenda`, { sinal });
}

export async function marcarFolga(dia: string): Promise<void> {
  await chamar<unknown>(`${BASE}/agenda/folgas/${encodeURIComponent(dia)}`, { metodo: 'PUT' });
}

export async function tirarFolga(dia: string): Promise<void> {
  await chamar<unknown>(`${BASE}/agenda/folgas/${encodeURIComponent(dia)}`, { metodo: 'DELETE' });
}
