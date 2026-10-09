import { expect, test } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi, simularConta } from './apiSimulada';

test.describe('Conta', () => {
  test('criar conta → minha conta → sair → entrar de novo, sem violação de CSP', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularConta(page);

    await page.goto('/criar-conta/cliente');
    await expect(page.getByRole('button', { name: 'Criar conta' })).toBeEnabled();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('textbox', { name: 'Seu nome' }).fill('Ana Souza');
    await page.getByRole('textbox', { name: 'Celular' }).fill('47900000001');
    await page.getByRole('textbox', { name: 'E-mail' }).fill('ana@exemplo.com');
    await page.getByRole('textbox', { name: 'Seu CEP' }).fill('89010000');
    await page.getByLabel('Crie uma senha').fill('Casa-Azul-2026');
    await page.getByRole('checkbox', { name: /Li e aceito/ }).check();
    await page.getByRole('button', { name: 'Criar conta' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    await expect(page.getByText('Enviamos um link para o seu e-mail.', { exact: false })).toBeVisible();
    await expect(page.getByText('Ana Souza', { exact: true })).toBeVisible();
    await semViolacoesAxe(page);

    await page.getByRole('button', { name: 'Sair', exact: true }).click();
    await page.getByRole('button', { name: 'Sim, sair' }).click();
    await expect(page).toHaveURL('/');

    await page.goto('/entrar');
    await page.getByRole('textbox', { name: 'Celular ou e-mail' }).fill('ana@exemplo.com');
    await page.getByLabel('Senha').fill('Casa-Azul-2026');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    expect(violacoes).toEqual([]);
  });

  test('entrar com segundo passo (código por SMS)', async ({ page }) => {
    await simularApi(page);
    await simularConta(page, { segundoPasso: true });

    await page.goto('/entrar');
    await page.getByRole('textbox', { name: 'Celular ou e-mail' }).fill('admin@exemplo.com');
    await page.getByLabel('Senha').fill('Casa-Azul-2026');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Digite o código que enviamos por SMS' })).toBeVisible();
    await semViolacoesAxe(page);
    await page.getByRole('textbox', { name: 'Código' }).fill('123456');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
  });

  test('página privada sem sessão leva a /entrar com o voltar', async ({ page }) => {
    await simularApi(page);

    await page.goto('/conta');

    await expect(page).toHaveURL('/entrar?voltar=%2Fconta');
    await expect(page.getByRole('heading', { level: 1, name: 'Entrar' })).toBeVisible();
  });
});
