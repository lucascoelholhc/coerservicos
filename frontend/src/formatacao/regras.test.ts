import { describe, expect, it } from 'vitest';

import { formatarPercentual, formatarPrazo } from './regras';

describe('formatação das regras', () => {
  it.each([
    ['PT12H', '12 h'],
    ['PT24H', '24 h'],
    ['PT90M', '1 h 30 min'],
    ['PT30M', '30 min'],
    ['PT1H30M', '1 h 30 min'],
  ])('prazo %s = "%s"', (iso, texto) => {
    expect(formatarPrazo(iso)).toBe(texto);
  });

  it.each([['P1D'], ['12h'], ['PT'], ['PT0M'], ['']])('prazo fora do formato (%s) lança erro', (iso) => {
    expect(() => formatarPrazo(iso)).toThrow();
  });

  it.each([
    ['0.1000', '10%'],
    ['0.10', '10%'],
    ['0.1250', '12,5%'],
    ['0.0750', '7,5%'],
    ['0.3000', '30%'],
    ['0.1234', '12,34%'],
  ])('percentual %s = "%s"', (fracao, texto) => {
    expect(formatarPercentual(fracao)).toBe(texto);
  });

  it.each([['dez'], ['1.5'], ['-0.1'], ['']])('percentual fora do formato (%s) lança erro', (fracao) => {
    expect(() => formatarPercentual(fracao)).toThrow();
  });
});
