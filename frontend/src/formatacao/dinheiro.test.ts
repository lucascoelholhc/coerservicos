import { describe, expect, it } from 'vitest';

import { centavosDoTexto, formatarReais, previaDaDiaria, textoDosCentavos } from './dinheiro';

describe('dinheiro no front (só exibição; o backend decide)', () => {
  it('formata o texto da API em reais', () => {
    expect(formatarReais('280.00')).toBe('R$ 280,00');
    expect(formatarReais('1234.5')).toBe('R$ 1.234,50');
    expect(formatarReais(0)).toBe('R$ 0,00');
  });

  it('lê o que a pessoa digitou (vírgula ou ponto) em centavos; inválido = null', () => {
    expect(centavosDoTexto('280')).toBe(28000);
    expect(centavosDoTexto('280,5')).toBe(28050);
    expect(centavosDoTexto(' 280.55 ')).toBe(28055);
    expect(centavosDoTexto('')).toBeNull();
    expect(centavosDoTexto('2,555')).toBeNull();
    expect(centavosDoTexto('-10')).toBeNull();
    expect(centavosDoTexto('abc')).toBeNull();
    expect(centavosDoTexto('1.234,00')).toBeNull();
  });

  it('centavos viram o texto que a API aceita', () => {
    expect(textoDosCentavos(28000)).toBe('280.00');
    expect(textoDosCentavos(5)).toBe('0.05');
  });

  it('prévia com a taxa paga pelo cliente: o profissional recebe a diária inteira', () => {
    expect(previaDaDiaria(28000, '0.1000', 'CLIENTE')).toEqual({ recebe: 28000, clientePaga: 30800 });
  });

  it('prévia com a taxa paga pelo profissional: desconta do que ele recebe', () => {
    expect(previaDaDiaria(28000, '0.1000', 'PROFISSIONAL')).toEqual({ recebe: 25200, clientePaga: 28000 });
  });

  it('arredonda a comissão como o backend (HALF_EVEN nos centavos)', () => {
    // 0,25 x 10% = 0,025 -> 0,02 (par); 0,35 x 10% = 0,035 -> 0,04 (par)
    expect(previaDaDiaria(25, '0.1000', 'CLIENTE').clientePaga).toBe(27);
    expect(previaDaDiaria(35, '0.1000', 'CLIENTE').clientePaga).toBe(39);
    // 12,5%: 1,01 x 0,125 = 0,12625 -> 0,13
    expect(previaDaDiaria(101, '0.125', 'CLIENTE').clientePaga).toBe(114);
  });

  it('comissão fora do formato = erro (nunca inventa um número)', () => {
    expect(() => previaDaDiaria(28000, '10%', 'CLIENTE')).toThrow();
  });
});
