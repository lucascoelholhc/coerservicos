import { expect, test, type Page, type Route } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi } from './apiSimulada';
import { HOST_DO_BUCKET_LOCAL, PERFIL_DE_EXEMPLO, RESULTADO_DA_BUSCA } from '../src/teste/dadosDaBusca';

/** PNG de 1x1 (o conteúdo não importa: importa vir do host do bucket e a CSP deixar). */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
);

const json = (rota: Route, corpo: unknown) =>
  rota.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(corpo) });

/** A API do DOM-04 e o bucket local (as fotos do perfil vêm de lá por URL assinada). */
async function simularBusca(pagina: Page) {
  const consultas: string[] = [];
  await pagina.route('**/api/publico/profissionais**', (rota) => {
    const url = new URL(rota.request().url());
    if (url.pathname === '/api/publico/profissionais') {
      consultas.push(url.search);
      return json(rota, RESULTADO_DA_BUSCA);
    }
    return json(rota, PERFIL_DE_EXEMPLO);
  });
  await pagina.route(`${HOST_DO_BUCKET_LOCAL}/**`, (rota) =>
    rota.fulfill({ status: 200, contentType: 'image/png', body: PNG }),
  );
  return consultas;
}

test.describe('Busca e perfil público', () => {
  test('herói do Início → busca com os filtros → perfil com a foto do bucket, sem violação de CSP', async ({
    page,
  }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    const consultas = await simularBusca(page);

    await page.goto('/');
    const heroi = page.getByRole('search', { name: 'Buscar profissional' });
    await heroi.getByLabel('Do que você precisa?').selectOption('pedreiro');
    await heroi.getByLabel('Em qual cidade?').selectOption('4202404');
    await heroi.getByRole('button', { name: 'Buscar' }).click();

    await expect(page).toHaveURL(/\/busca\?profissao=pedreiro&cidade=4202404$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Pedreiros em Blumenau' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Valdir S.' })).toBeVisible();
    expect(consultas.at(-1)).toBe('?profissao=pedreiro&cidade=4202404&pagina=0');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    await page.getByRole('link', { name: 'Valdir S.' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Valdir S.' })).toBeVisible();
    const foto = page.getByRole('img', { name: 'Muro de bloco' });
    await expect(foto).toBeVisible();
    expect(await foto.evaluate((imagem: HTMLImageElement) => imagem.naturalWidth)).toBe(1);
    await expect(page.getByText('Telefone e WhatsApp aparecem depois de contratar.')).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    await page.getByRole('link', { name: 'Voltar para a busca' }).click();
    await expect(page).toHaveURL(/\/busca\?profissao=pedreiro&cidade=4202404$/);
    expect(violacoes).toEqual([]);
  });

  test('filtros: no celular pela folha, na tela larga pela lateral; a etiqueta remove o filtro', async ({
    page,
  }, info) => {
    await simularApi(page);
    const consultas = await simularBusca(page);
    await page.goto('/busca');
    await expect(page.getByRole('link', { name: 'Valdir S.' })).toBeVisible();

    if (info.project.name === 'celular') {
      await expect(page.getByRole('complementary', { name: 'Filtros' })).toBeHidden();
      await page.getByRole('button', { name: 'Filtros', exact: true }).click();
      const folha = page.getByRole('dialog', { name: 'Filtros' });
      await folha.getByLabel('Cidade do serviço').selectOption('4202404');
      await expect(folha.getByRole('button', { name: 'Ver 2 profissionais' })).toBeVisible();
      await semViolacoesAxe(page);
      await page.keyboard.press('Escape');
      await expect(folha).toBeHidden();
    } else {
      await expect(page.getByRole('button', { name: 'Filtros', exact: true })).toBeHidden();
      await page.getByRole('complementary', { name: 'Filtros' }).getByLabel('Cidade do serviço').selectOption('4202404');
    }

    await expect(page).toHaveURL(/cidade=4202404/);
    await page.getByRole('button', { name: 'Remover filtro Blumenau' }).click();
    await expect(page).toHaveURL(/\/busca$/);
    expect(consultas.at(-1)).toBe('?pagina=0');
    await semRolagemLateral(page);
  });
});
