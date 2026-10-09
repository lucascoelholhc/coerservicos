import type { Page, Route } from '@playwright/test';

import { CATALOGO_PADRAO, REGRAS_PADRAO } from '../src/teste/dadosDaApi';

export type Comportamento = 'ok' | 'erro' | 'lento';

async function responder(rota: Route, corpo: unknown, comportamento: Comportamento) {
  if (comportamento === 'erro') {
    await rota.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      body: JSON.stringify({ type: 'urn:coe:erro:erro-interno', title: 'Erro', status: 500, detail: 'x' }),
    });
    return;
  }
  if (comportamento === 'lento') {
    await new Promise((resolver) => setTimeout(resolver, 1500));
  }
  await rota.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(corpo) });
}

/**
 * O vite preview não tem backend: as chamadas a /api/publico/* respondem daqui (mesma origem, então a
 * CSP connect-src 'self' vale como em produção). Devolve uma função para trocar o comportamento.
 */
export async function simularApi(pagina: Page, inicial: { catalogo?: Comportamento; regras?: Comportamento } = {}) {
  const atual = { catalogo: inicial.catalogo ?? 'ok', regras: inicial.regras ?? 'ok' };
  await pagina.route('**/api/publico/catalogo', (rota) => responder(rota, CATALOGO_PADRAO, atual.catalogo));
  await pagina.route('**/api/publico/regras', (rota) => responder(rota, REGRAS_PADRAO, atual.regras));
  return (novo: { catalogo?: Comportamento; regras?: Comportamento }) => Object.assign(atual, novo);
}
