import { expect, test, type Page, type Route } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi } from './apiSimulada';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../src/teste/dadosDaApi';
import { DETALHE_DA_VERIFICACAO, FILA_DA_VERIFICACAO } from '../src/teste/dadosDaVerificacao';

const json = (rota: Route, corpo: unknown, status = 200) =>
  rota.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });

/** Sessão de ADMIN (a renovação silenciosa ao abrir já devolve o papel) e a API do DOM-03. */
async function simularVerificacao(pagina: Page, papeis: string[] = ['ADMIN']) {
  const decisoes: { url: string; corpo: unknown }[] = [];
  await pagina.route('**/api/auth/renovar', (rota) =>
    json(rota, { ...SESSAO_PADRAO, usuario: { ...SESSAO_PADRAO.usuario, papeis } }),
  );
  await pagina.route('**/api/contas/eu', (rota) => json(rota, { ...CONTA_PADRAO, papeis }));
  await pagina.route('**/api/admin/verificacao**', async (rota) => {
    const pedido = rota.request();
    const caminho = new URL(pedido.url()).pathname;
    if (pedido.method() === 'POST') {
      decisoes.push({ url: caminho, corpo: pedido.postDataJSON() });
      return json(rota, { id: 'p1', status: 'ativo', nr10: 'nao_tem' });
    }
    if (caminho.endsWith('/url')) {
      return json(rota, { url: 'https://bucket.exemplo/documento/rg.jpg?assinatura', expiraEm: '2026-10-22T12:02:00Z' });
    }
    if (caminho === '/api/admin/verificacao') {
      return json(rota, decisoes.length > 0 ? { itens: [FILA_DA_VERIFICACAO.itens[1]], temMais: false } : FILA_DA_VERIFICACAO);
    }
    return json(rota, DETALHE_DA_VERIFICACAO);
  });
  return decisoes;
}

test.describe('Verificação de cadastros (admin)', () => {
  test('fila → conferir → aprovar → volta para a fila com aviso, sem violação de CSP', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    const decisoes = await simularVerificacao(page);

    await page.goto('/admin/verificacao');
    await expect(page.getByRole('heading', { level: 1, name: 'Cadastros para verificar' })).toBeVisible();
    await expect(page.getByRole('article')).toHaveCount(2);
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    await page.getByRole('link', { name: 'Analisar o cadastro de Valdir Schmitt' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Valdir Schmitt' })).toBeVisible();
    await expect(page.getByText('111.444.777-35')).toBeVisible();
    await page.getByRole('button', { name: 'Ver RG (frente)' }).click();
    await expect(page.getByRole('link', { name: 'Abrir RG (frente)' })).toHaveAttribute('target', '_blank');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    for (const item of [/CPF válido/, /Documento legível/, /Selfie confere/, /Fotos parecem/, /Texto do perfil/]) {
      await page.getByRole('checkbox', { name: item }).check();
    }
    await page.getByRole('button', { name: 'Aprovar e publicar' }).click();

    await expect(page.getByText('Cadastro aprovado. O profissional já aparece na busca.')).toBeVisible();
    await expect(page.getByRole('article')).toHaveCount(1);
    expect(decisoes).toEqual([
      { url: '/api/admin/verificacao/p1/aprovar', corpo: { itens: { cpf: true, documento: true, selfie: true, fotos: true, texto: true } } },
    ]);
    expect(violacoes).toEqual([]);
  });

  test('quem não é ADMIN vê "sem acesso"', async ({ page }) => {
    await simularApi(page);
    await simularVerificacao(page, ['CLIENTE']);

    await page.goto('/admin/verificacao');

    await expect(page.getByRole('heading', { level: 1, name: 'Você não tem acesso a esta página' })).toBeVisible();
  });
});
