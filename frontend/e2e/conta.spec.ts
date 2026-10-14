import { expect, test } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi, simularConta } from './apiSimulada';
import { SESSAO_PADRAO } from '../src/teste/dadosDaApi';

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
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    expect(violacoes).toEqual([]);
  });

  test('entrar com segundo passo (código por SMS)', async ({ page }) => {
    await simularApi(page);
    await simularConta(page, { segundoPasso: true });

    await page.goto('/entrar');
    await page.getByRole('textbox', { name: 'Celular ou e-mail' }).fill('admin@exemplo.com');
    await page.getByLabel('Senha').fill('Casa-Azul-2026');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Digite o código que enviamos por SMS' })).toBeVisible();
    await semViolacoesAxe(page);
    await page.getByRole('textbox', { name: 'Código' }).fill('123456');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
  });

  test('página privada sem sessão leva a /entrar com o voltar', async ({ page }) => {
    await simularApi(page);

    await page.goto('/conta');

    await expect(page).toHaveURL('/entrar?voltar=%2Fconta');
    await expect(page.getByRole('heading', { level: 1, name: 'Entrar' })).toBeVisible();
  });
});

test.describe('Links do e-mail', () => {
  test('esqueci → redefinir pelo link: fragmento limpo antes da chamada e volta para entrar', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await page.route('**/api/auth/senha/esqueci', (rota) => rota.fulfill({ status: 202 }));
    let corpoDoRedefinir: unknown = null;
    let urlNaHoraDoRedefinir = '';
    await page.route('**/api/auth/senha/redefinir', async (rota) => {
      corpoDoRedefinir = rota.request().postDataJSON();
      urlNaHoraDoRedefinir = page.url();
      await rota.fulfill({ status: 204 });
    });

    await page.goto('/esqueci-senha');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('textbox', { name: 'Celular ou e-mail' }).fill('ana@exemplo.com');
    await page.getByRole('button', { name: 'Recuperar senha' }).click();
    await expect(page.getByText('Se existir uma conta, enviamos um link', { exact: false })).toBeVisible();

    await page.goto('/redefinir-senha#token=tok-e2e');
    await expect(page.getByRole('heading', { level: 1, name: 'Criar nova senha' })).toBeVisible();
    await expect(page).toHaveURL('/redefinir-senha');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByLabel('Nova senha').fill('Casa-Verde-2026');
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();

    await expect(page.getByText('Senha alterada. Entre com a nova senha.')).toBeVisible();
    await expect(page).toHaveURL('/entrar');
    expect(corpoDoRedefinir).toEqual({ token: 'tok-e2e', novaSenha: 'Casa-Verde-2026' });
    expect(urlNaHoraDoRedefinir).not.toContain('tok-e2e');
    expect(violacoes).toEqual([]);
  });

  test('confirmar e-mail com token inválido', async ({ page }) => {
    await simularApi(page);
    await page.route('**/api/contas/email/confirmar', (rota) =>
      rota.fulfill({
        status: 422,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'urn:coe:erro:link-invalido',
          title: 'Erro',
          status: 422,
          detail: 'Este link não vale mais. Peça outro.',
        }),
      }),
    );

    await page.goto('/confirmar-email#token=vencido');

    await expect(page.getByRole('heading', { level: 1, name: 'Este link não vale mais' })).toBeVisible();
    await expect(page).toHaveURL('/confirmar-email');
    await expect(page.getByRole('link', { name: 'Entrar' }).last()).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
  });
});

test.describe('Código por SMS', () => {
  test('entrar com código: celular → código → Minha conta → seção Segurança', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularConta(page);
    await page.route('**/api/auth/codigo', (rota) =>
      rota.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ mensagem: 'Se o celular tiver conta, enviamos um código por SMS.' }),
      }),
    );
    let corpoDoEntrar: unknown = null;
    await page.route('**/api/auth/entrar-com-codigo', async (rota) => {
      corpoDoEntrar = rota.request().postDataJSON();
      await rota.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SESSAO_PADRAO) });
    });

    await page.goto('/entrar');
    await page.getByRole('button', { name: 'Entrar com código por SMS' }).click();
    await page.getByRole('textbox', { name: 'Celular' }).fill('47900000001');
    await page.getByRole('button', { name: 'Mandar código' }).click();

    await expect(page.getByText('Pode pedir outro código em', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Mandar outro código' })).toBeDisabled();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('textbox', { name: 'Código' }).fill('123456');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByRole('heading', { level: 2, name: 'Segurança' })).toBeVisible();
    expect(corpoDoEntrar).toEqual({ celular: '47900000001', codigo: '123456' });
    await page.getByRole('link', { name: 'Código no login' }).click();
    await expect(page.getByText('Para usar o código no login, confirme seu celular primeiro.')).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    expect(violacoes).toEqual([]);
  });
});

test.describe('Trocar senha', () => {
  test('logado: Minha conta → Trocar senha → volta com aviso, sessão mantida', async ({ page }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularConta(page);
    let corpoDaTroca: unknown = null;
    await page.route('**/api/auth/senha/trocar', async (rota) => {
      corpoDaTroca = rota.request().postDataJSON();
      await rota.fulfill({ status: 204 });
    });

    await page.goto('/entrar');
    await page.getByRole('textbox', { name: 'Celular ou e-mail' }).fill('ana@exemplo.com');
    await page.getByLabel('Senha').fill('Casa-Azul-2026');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await page.getByRole('link', { name: 'Trocar senha' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Trocar senha' })).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByLabel('Senha atual').fill('Casa-Azul-2026');
    await page.getByLabel('Nova senha').fill('Casa-Verde-2026');
    await page.getByRole('button', { name: 'Trocar senha' }).click();

    await expect(page.getByText('Senha trocada. Os outros aparelhos foram desconectados.')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    expect(corpoDaTroca).toEqual({ senhaAtual: 'Casa-Azul-2026', novaSenha: 'Casa-Verde-2026' });
    expect(violacoes).toEqual([]);
  });
});

test.describe('Prova de posse do e-mail (RN61)', () => {
  test('duas abas: o link abre a /provar-email e a aba do cadastro recebe o comprovante e termina', async ({
    context,
    page,
  }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularConta(page);
    const cadastros: unknown[] = [];
    await page.route('**/api/contas/cliente', async (rota) => {
      cadastros.push(rota.request().postDataJSON());
      if (cadastros.length === 1) {
        await rota.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'urn:coe:erro:contato-em-uso',
            title: 'Erro',
            status: 409,
            detail: 'Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.',
            campo: 'email',
          }),
        });
        return;
      }
      await rota.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'u-ana', nome: 'Ana Souza' }) });
    });
    await context.route('**/api/contas/posse/email', (rota) =>
      rota.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ mensagem: 'Enviamos.' }) }),
    );
    await context.route('**/api/contas/posse/email/confirmar', (rota) =>
      rota.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ comprovante: 'comp-e2e', email: 'ana@exemplo.com' }),
      }),
    );

    await page.goto('/criar-conta/cliente');
    await page.getByRole('textbox', { name: 'Seu nome' }).fill('Ana Souza');
    await page.getByRole('textbox', { name: 'Celular' }).fill('47900000001');
    await page.getByRole('textbox', { name: 'E-mail' }).fill('ana@exemplo.com');
    await page.getByRole('textbox', { name: 'Seu CEP' }).fill('89010000');
    await page.getByLabel('Crie uma senha').fill('Casa-Azul-2026');
    await page.getByRole('checkbox', { name: /Li e aceito/ }).check();
    await page.getByRole('button', { name: 'Criar conta' }).click();
    await page.getByRole('button', { name: 'Esse e-mail é meu' }).click();
    await expect(page.getByText('Enviamos um link para ana@exemplo.com', { exact: false })).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    const outraAba = await context.newPage();
    const violacoesDaOutra = await vigiarCsp(outraAba);
    await simularApi(outraAba);
    await outraAba.goto('/provar-email#token=tok-e2e');

    await expect(outraAba.getByText('Pronto. Volte para a aba do cadastro para terminar.')).toBeVisible();
    await expect(outraAba).toHaveURL('/provar-email');
    await semViolacoesAxe(outraAba);
    await semRolagemLateral(outraAba);
    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    expect(cadastros[1]).toMatchObject({ email: 'ana@exemplo.com', comprovanteEmail: 'comp-e2e' });
    expect(violacoes).toEqual([]);
    expect(violacoesDaOutra).toEqual([]);
  });
});
