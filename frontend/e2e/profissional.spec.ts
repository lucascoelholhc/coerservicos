import { expect, test, type Page, type Route } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi } from './apiSimulada';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../src/teste/dadosDaApi';
import { CADASTRO_COMPLETO } from '../src/teste/dadosDoCadastro';
import { CONTRATO_PAGO } from '../src/teste/dadosDosContratos';
import { AGENDA, DIARIA_DE_HOJE, DIARIA_EM_ANDAMENTO, DIARIA_FUTURA, DINHEIRO, HOJE } from '../src/teste/dadosDoProfissional';

const json = (rota: Route, corpo: unknown, status = 200) =>
  rota.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });

/** Um JPEG mínimo (1×1) para o "Terminei o dia". */
const JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
  'base64',
);

/** Profissional logado e a API do FE-06: a diária de hoje anda com o Cheguei e o Terminei. */
async function simularProfissional(pagina: Page) {
  let diaria = DIARIA_DE_HOJE;
  const sessao = {
    ...SESSAO_PADRAO,
    usuario: { ...SESSAO_PADRAO.usuario, nome: 'Valdir Souza', papeis: ['CLIENTE', 'PROFISSIONAL'] },
  };
  await pagina.route('**/api/auth/renovar', (rota) => json(rota, sessao));
  await pagina.route('**/api/contas/eu', (rota) => json(rota, CONTA_PADRAO));
  await pagina.route('**/api/profissional/**', async (rota) => {
    const caminho = new URL(rota.request().url()).pathname;
    if (caminho === '/api/profissional/diarias') {
      return json(rota, { hoje: HOJE, itens: [diaria, DIARIA_FUTURA] });
    }
    if (caminho === '/api/profissional/dinheiro') {
      return json(rota, DINHEIRO);
    }
    if (caminho === '/api/profissional/agenda') {
      return json(rota, AGENDA);
    }
    if (caminho === '/api/profissional/cadastro') {
      return json(rota, { ...CADASTRO_COMPLETO, status: 'ativo', chavePix: '••••-4471' });
    }
    return rota.fulfill({ status: 204 });
  });
  await pagina.route(`**/api/contratos/${CONTRATO_PAGO.id}`, (rota) => json(rota, CONTRATO_PAGO));
  await pagina.route('**/api/diarias/**', async (rota) => {
    const caminho = new URL(rota.request().url()).pathname;
    if (caminho.endsWith('/cheguei')) {
      diaria = DIARIA_EM_ANDAMENTO;
      return json(rota, { ...diaria, liberadaEm: null, liberadaPor: null });
    }
    if (caminho === '/api/diarias/fotos') {
      return json(rota, { id: 'foto-1' }, 201);
    }
    diaria = { ...DIARIA_EM_ANDAMENTO, status: 'aguardando', autoLiberaEm: '2026-11-20T07:00:00Z' };
    return json(rota, { ...diaria, liberadaEm: null, liberadaPor: null });
  });
}

test.describe('Área do profissional (FE-06)', () => {
  test('Hoje → Cheguei → Terminei com foto → Agenda com folga → Dinheiro, sem violação de CSP', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularProfissional(page);

    await page.goto('/profissional');
    await expect(page.getByRole('heading', { level: 2, name: 'Obra de Carla M.' })).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    await page.getByRole('button', { name: /Cheguei na obra/ }).click();
    await page.getByRole('link', { name: /Terminei o dia/ }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Terminei o dia' })).toBeVisible();
    await page
      .getByLabel('Foto do que você fez hoje')
      .setInputFiles({ name: 'servico.jpg', mimeType: 'image/jpeg', buffer: JPEG });
    await expect(page.getByText('1 foto enviada.')).toBeVisible();
    await page.getByRole('button', { name: 'Deixei tudo limpo' }).click();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('button', { name: /Enviar e terminar o dia/ }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Dia enviado' })).toBeVisible();

    await page.goto('/profissional/agenda');
    await page.getByRole('button', { name: /: Livre$/ }).first().click();
    await expect(page.getByText(/é folga\./)).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    await page.goto('/profissional/dinheiro');
    await expect(page.getByText('Liberado para você')).toBeVisible();
    await expect(page.getByText('R$ 840,00')).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    expect(violacoes).toEqual([]);
  });
});
