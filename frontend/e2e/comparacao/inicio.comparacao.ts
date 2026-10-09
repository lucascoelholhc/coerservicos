import { mkdirSync, writeFileSync } from 'node:fs';

import { test, type Page } from '@playwright/test';

import { simularApi } from '../apiSimulada';

/**
 * Início do protótipo (ef7d397) x Início do React, lado a lado, em test-results/comparacao/ (fora do
 * git). A barra de controles do protótipo é escondida: ela não faz parte do produto.
 */
const DESTINO = new URL('../../test-results/comparacao/', import.meta.url);
const TAMANHOS = [
  { nome: '360x740', width: 360, height: 740 },
  { nome: '1280x800', width: 1280, height: 800 },
];

async function capturar(pagina: Page, endereco: string, esconder?: string): Promise<Buffer> {
  await pagina.goto(endereco, { waitUntil: 'networkidle' });
  if (esconder) await pagina.addStyleTag({ content: `${esconder} { display: none !important; }` });
  await pagina.evaluate(() => document.fonts.ready);
  // Deixa o carimbo terminar de bater
  await pagina.waitForTimeout(1500);
  return pagina.screenshot({ fullPage: true });
}

for (const tamanho of TAMANHOS) {
  test(`Início ${tamanho.nome}: protótipo x React`, async ({ browser }) => {
    const contexto = await browser.newContext({ viewport: { width: tamanho.width, height: tamanho.height } });
    const pagina = await contexto.newPage();
    const prototipo = await capturar(pagina, 'http://localhost:4174/#inicio', '.proto');
    await simularApi(pagina);
    const react = await capturar(pagina, 'http://localhost:4173/');

    // Junta as duas capturas numa imagem só, com o rótulo de cada lado
    const montagem = await browser.newPage({ viewport: { width: tamanho.width * 2 + 48, height: 600 } });
    await montagem.setContent(`<!doctype html><html><body style="margin:0;background:#888;font:600 16px sans-serif">
      <div style="display:flex;gap:16px;padding:16px;align-items:flex-start">
        <figure style="margin:0"><figcaption style="color:#fff;padding-bottom:8px">Protótipo ef7d397</figcaption>
          <img src="data:image/png;base64,${prototipo.toString('base64')}" width="${tamanho.width}"></figure>
        <figure style="margin:0"><figcaption style="color:#fff;padding-bottom:8px">React (FE-01)</figcaption>
          <img src="data:image/png;base64,${react.toString('base64')}" width="${tamanho.width}"></figure>
      </div></body></html>`);
    await montagem.evaluate(() => Promise.all([...document.images].map((imagem) => imagem.decode())));

    mkdirSync(DESTINO, { recursive: true });
    writeFileSync(new URL(`prototipo-${tamanho.nome}.png`, DESTINO), prototipo);
    writeFileSync(new URL(`react-${tamanho.nome}.png`, DESTINO), react);
    writeFileSync(new URL(`lado-a-lado-${tamanho.nome}.png`, DESTINO), await montagem.screenshot({ fullPage: true }));
    await contexto.close();
  });
}
