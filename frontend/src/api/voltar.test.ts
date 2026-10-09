import { describe, expect, it } from 'vitest';

import { caminhoDeEntrar, caminhoInternoSeguro } from './voltar';

describe('caminho de volta seguro (sem redirecionamento aberto)', () => {
  it.each([
    ['/busca?profissao=pintor', '/busca?profissao=pintor'],
    ['/conta#dados', '/conta#dados'],
    ['/', '/'],
  ])('aceita caminho interno %s', (valor, esperado) => {
    expect(caminhoInternoSeguro(valor)).toBe(esperado);
  });

  it.each([
    ['//evil.com'],
    ['/\\evil.com'],
    ['https://evil.com'],
    ['javascript:alert(1)'],
    ['evil.com'],
    ['/%2F%2Fevil.com'],
    ['/%E0%A4%A'],
    ['/caminho\u0000nulo'],
    [''],
    [null],
    [undefined],
    ['/entrar?voltar=/conta'],
  ])('recusa %s e volta ao início', (valor) => {
    expect(caminhoInternoSeguro(valor)).toBe('/');
  });

  it('monta /entrar com o voltar codificado', () => {
    expect(caminhoDeEntrar('/busca?profissao=pintor&cidade=4202404')).toBe(
      '/entrar?voltar=%2Fbusca%3Fprofissao%3Dpintor%26cidade%3D4202404',
    );
    expect(caminhoDeEntrar('//evil.com')).toBe('/entrar?voltar=%2F');
  });
});
