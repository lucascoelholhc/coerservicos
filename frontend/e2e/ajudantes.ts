import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

const NORMAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

/** Guarda toda violação de CSP (evento do navegador e aviso do console) desde o início da página. */
export async function vigiarCsp(pagina: Page): Promise<string[]> {
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

export async function semRolagemLateral(pagina: Page) {
  const larguras = await pagina.evaluate(() => ({
    pagina: document.documentElement.scrollWidth,
    janela: window.innerWidth,
  }));
  expect(larguras.pagina).toBeLessThanOrEqual(larguras.janela);
}

export async function semViolacoesAxe(pagina: Page) {
  // O carimbo anima a opacidade ao aparecer: o contraste só vale depois da animação.
  await pagina.evaluate(() => Promise.all(document.getAnimations().map((animacao) => animacao.finished)));
  const resultado = await new AxeBuilder({ page: pagina }).withTags(NORMAS_WCAG).analyze();
  expect(resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual(
    [],
  );
}
