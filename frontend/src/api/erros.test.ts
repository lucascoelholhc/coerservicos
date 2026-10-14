import { describe, expect, it } from 'vitest';

import { comoErroDaApi, ErroDaApi, MENSAGENS } from './erros';

describe('comoErroDaApi', () => {
  it('mantém o ErroDaApi e troca qualquer outra falha por "servidor"', () => {
    const daApi = new ErroDaApi('limite');

    expect(comoErroDaApi(daApi)).toBe(daApi);
    expect(comoErroDaApi(new Error('boom'))).toMatchObject({ tipo: 'servidor', message: MENSAGENS.servidor });
  });
});
