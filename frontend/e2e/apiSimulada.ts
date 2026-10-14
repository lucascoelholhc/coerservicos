import type { Page, Route } from '@playwright/test';

import { CATALOGO_PADRAO, CONTA_PADRAO, REGRAS_PADRAO, SESSAO_PADRAO } from '../src/teste/dadosDaApi';

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
  // Sem cookie do refresh: quem abre o app é visitante (renovação silenciosa = 401).
  await pagina.route('**/api/auth/renovar', (rota) => problema(rota, 401, 'sessao-invalida', 'Entre de novo.'));
  return (novo: { catalogo?: Comportamento; regras?: Comportamento }) => Object.assign(atual, novo);
}

async function problema(rota: Route, status: number, codigo: string, detalhe: string, extra: object = {}) {
  await rota.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: `urn:coe:erro:${codigo}`, title: 'Erro', status, detail: detalhe, ...extra }),
  });
}

/** Cadastro, entrar (com ou sem segundo passo), minha conta e sair, como o backend responde. */
export async function simularConta(pagina: Page, opcoes: { segundoPasso?: boolean } = {}) {
  const json = (rota: Route, corpo: unknown, status = 200) =>
    rota.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });
  await pagina.route('**/api/contas/cliente', (rota) => json(rota, { id: 'u-ana', nome: 'Ana Souza' }, 201));
  await pagina.route('**/api/auth/entrar', (rota) =>
    opcoes.segundoPasso
      ? problema(rota, 403, 'segundo-passo-necessario', 'Digite o código que enviamos por SMS.', { desafioId: 'd-1' })
      : json(rota, SESSAO_PADRAO),
  );
  await pagina.route('**/api/auth/segundo-passo', (rota) => json(rota, SESSAO_PADRAO));
  await pagina.route('**/api/contas/eu', (rota) => json(rota, CONTA_PADRAO));
  await pagina.route('**/api/auth/sair', (rota) => rota.fulfill({ status: 204 }));
}
