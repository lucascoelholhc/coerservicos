import axe from 'axe-core';
import { expect } from 'vitest';

/**
 * Nenhuma violação de acessibilidade (axe) no elemento. O contraste de cor fica com o Playwright
 * (o jsdom não calcula cores); aqui vale o resto: rótulos, papéis, nomes acessíveis, estrutura.
 */
export async function semViolacoes(elemento: Element): Promise<void> {
  const resultado = await axe.run(elemento, { rules: { 'color-contrast': { enabled: false } } });
  const violacoes = resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.html).join(' | ')}`);
  expect(violacoes).toEqual([]);
}
