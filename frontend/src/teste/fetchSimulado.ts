import { vi } from 'vitest';

export interface Pedido {
  url: string;
  metodo: string;
  cabecalhos: Record<string, string>;
  corpo: string | undefined;
  sinal: AbortSignal | undefined;
}

type Responder = (pedido: Pedido) => Response | Promise<Response>;

/** Resposta JSON (ou Problem Details) para o fetch simulado. */
export function json(corpo: unknown, status = 200, tipo = 'application/json'): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': tipo } });
}

export function problema(status: number, codigo: string, detalhe: string, extra: Record<string, unknown> = {}): Response {
  return json(
    { type: `urn:coe:erro:${codigo}`, title: 'Erro', status, detail: detalhe, ...extra },
    status,
    'application/problem+json',
  );
}

/** Troca o fetch global por um que registra os pedidos e responde pela função dada. */
export function simularFetch(responder: Responder) {
  const pedidos: Pedido[] = [];
  const falso = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    const pedido: Pedido = {
      url: String(entrada),
      metodo: init.method ?? 'GET',
      cabecalhos: { ...(init.headers as Record<string, string> | undefined) },
      corpo: typeof init.body === 'string' ? init.body : undefined,
      sinal: init.signal ?? undefined,
    };
    pedidos.push(pedido);
    return responder(pedido);
  });
  vi.stubGlobal('fetch', falso);
  return { pedidos, falso };
}

/** fetch que só termina quando o sinal for abortado (para timeout e cancelamento). */
export function esperarAborto(pedido: Pedido): Promise<Response> {
  return new Promise((_, rejeitar) => {
    pedido.sinal?.addEventListener('abort', () => rejeitar(pedido.sinal?.reason));
  });
}
