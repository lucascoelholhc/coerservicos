import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

import cabecalhos from '../cabecalhos-seguranca.json' with { type: 'json' };

const NORMAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

/** Guarda toda violação de CSP (evento do navegador e aviso do console) desde o início da página. */
async function vigiarCsp(pagina: Page): Promise<string[]> {
  const violacoes: string[] = [];
  pagina.on('console', (mensagem) => {
    if (/Content Security Policy/i.test(mensagem.text())) violacoes.push(mensagem.text());
  });
  await pagina.exposeFunction('registrarViolacaoCsp', (texto: string) => violacoes.push(texto));
  await pagina.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (evento) => {
      (window as unknown as { registrarViolacaoCsp: (t: string) => void }).registrarViolacaoCsp(
        `${evento.violatedDirective} ${evento.blockedURI}`,
      );
    });
  });
  return violacoes;
}

async function semRolagemLateral(pagina: Page) {
  const larguras = await pagina.evaluate(() => ({
    pagina: document.documentElement.scrollWidth,
    janela: window.innerWidth,
  }));
  expect(larguras.pagina).toBeLessThanOrEqual(larguras.janela);
}

async function semViolacoesAxe(pagina: Page) {
  const resultado = await new AxeBuilder({ page: pagina }).withTags(NORMAS_WCAG).analyze();
  expect(resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual(
    [],
  );
}

test.describe('Início', () => {
  test('cabeçalhos de segurança, nenhuma violação de CSP e nenhuma rolagem lateral', async ({ page }) => {
    const violacoes = await vigiarCsp(page);

    const resposta = await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: /Pague por dia/ })).toBeVisible();
    await page.evaluate(() => document.fonts.ready);

    expect(resposta?.headers()['content-security-policy']).toBe(cabecalhos['Content-Security-Policy']);
    expect(resposta?.headers()['permissions-policy']).toBe(cabecalhos['Permissions-Policy']);
    expect(violacoes).toEqual([]);
    await semRolagemLateral(page);
  });

  test('menu de baixo só no celular; menu de cima só na tela larga', async ({ page }, info) => {
    await page.goto('/');
    const menuDeBaixo = page.getByRole('navigation', { name: 'Menu' });
    const menuDeCima = page.getByRole('navigation', { name: 'Principal' });

    if (info.project.name === 'celular') {
      await expect(menuDeBaixo).toBeVisible();
      await expect(menuDeCima).toBeHidden();
      await expect(menuDeBaixo.getByRole('link', { name: 'Início' })).toHaveAttribute('aria-current', 'page');
    } else {
      await expect(menuDeBaixo).toBeHidden();
      await expect(menuDeCima).toBeVisible();
    }
  });

  test('"Pular para o conteúdo" pelo teclado, com foco visível de 3 px', async ({ page }) => {
    await page.goto('/');

    await page.keyboard.press('Tab');
    const pular = page.getByRole('link', { name: 'Pular para o conteúdo' });
    await expect(pular).toBeFocused();
    await expect(pular).toBeInViewport();
    const contorno = await pular.evaluate((elemento) => {
      const estilo = getComputedStyle(elemento);
      return `${estilo.outlineStyle} ${estilo.outlineWidth}`;
    });
    expect(contorno).toBe('solid 3px');

    await page.keyboard.press('Enter');
    await expect(page.getByRole('main')).toBeFocused();
  });

  test('foco visível no "Buscar" pelo teclado', async ({ page }) => {
    await page.goto('/');
    const buscar = page.getByRole('button', { name: 'Buscar' });

    await buscar.focus();
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Tab');

    await expect(buscar).toBeFocused();
    const contorno = await buscar.evaluate((elemento) => {
      const estilo = getComputedStyle(elemento);
      return `${estilo.outlineStyle} ${estilo.outlineWidth}`;
    });
    expect(contorno).toBe('solid 3px');
  });

  test('Archivo da própria origem com o eixo de largura: título a 125% e mais largo que a 100%', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.fonts.ready);

    const medida = await page.evaluate(() => {
      const titulo = document.querySelector('h1');
      const medir = (largura: string) => {
        const amostra = document.createElement('span');
        amostra.textContent = 'Pague por dia';
        amostra.style.font = `800 40px "Archivo Variable"`;
        amostra.style.fontStretch = largura;
        amostra.style.position = 'absolute';
        document.body.append(amostra);
        const resultado = amostra.getBoundingClientRect().width;
        amostra.remove();
        return resultado;
      };
      return {
        carregada: document.fonts.check('800 40px "Archivo Variable"'),
        larguraDoTitulo: titulo ? getComputedStyle(titulo).fontStretch : '',
        a100: medir('100%'),
        a125: medir('125%'),
      };
    });

    expect(medida.carregada).toBe(true);
    expect(medida.larguraDoTitulo).toBe('125%');
    expect(medida.a125).toBeGreaterThan(medida.a100 * 1.1);
  });

  test('acessibilidade (axe, WCAG 2.1 AA, com contraste)', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => document.fonts.ready);

    await semViolacoesAxe(page);
  });
});

test.describe('Links para seções do Início', () => {
  test('do rodapé de outra página: navega sem recarregar e mostra a seção', async ({ page }) => {
    await page.goto('/essa-pagina-nao-existe');
    await page.evaluate(() => {
      (window as unknown as { semRecarregar: boolean }).semRecarregar = true;
    });

    await page.getByRole('contentinfo').getByRole('link', { name: 'Como funciona' }).click();

    await expect(page).toHaveURL('/#como-funciona');
    await expect(page.getByRole('heading', { level: 2, name: 'Como funciona' })).toBeInViewport();
    expect(await page.evaluate(() => (window as unknown as { semRecarregar?: boolean }).semRecarregar)).toBe(true);
  });
});

test.describe('Página não encontrada', () => {
  test('rota que não existe mostra "Página não encontrada" e o botão leva ao início', async ({ page }) => {
    const violacoes = await vigiarCsp(page);

    await page.goto('/essa-pagina-nao-existe');
    await expect(page.getByRole('heading', { level: 1, name: 'Página não encontrada' })).toBeVisible();
    await expect(page).toHaveTitle('Página não encontrada · COE Serviços');
    await semRolagemLateral(page);
    await semViolacoesAxe(page);

    await page.getByRole('link', { name: 'Ir para o início' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1, name: /Pague por dia/ })).toBeVisible();
    expect(violacoes).toEqual([]);
  });
});
