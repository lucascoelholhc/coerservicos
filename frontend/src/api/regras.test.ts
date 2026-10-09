import { afterEach, describe, expect, it, vi } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { ErroDaApi } from './erros';
import { buscarRegras } from './regras';

describe('regras públicas', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lê /api/publico/regras e devolve tipado', async () => {
    const { pedidos } = simularFetch(() => json({ comissao: '0.1000', prazoLiberacao: 'PT12H', taxaPagaPor: 'CLIENTE' }));

    await expect(buscarRegras(new AbortController().signal)).resolves.toEqual({
      comissao: '0.1000',
      prazoLiberacao: 'PT12H',
      taxaPagaPor: 'CLIENTE',
    });
    expect(pedidos[0]?.url).toBe('/api/publico/regras');
  });

  it.each([
    [{ comissao: '0.1000', prazoLiberacao: 'PT12H', taxaPagaPor: 'AMBOS' }],
    [{ comissao: 'dez', prazoLiberacao: 'PT12H', taxaPagaPor: 'CLIENTE' }],
    [{ comissao: '0.1000', prazoLiberacao: '12 horas', taxaPagaPor: 'CLIENTE' }],
    [{ comissao: '0.1000', taxaPagaPor: 'CLIENTE' }],
  ])('valor desconhecido ou fora do formato vira erro de resposta: %o', async (corpo) => {
    simularFetch(() => json(corpo));

    const erro = await buscarRegras(new AbortController().signal).catch((falha: unknown) => falha);

    expect(erro).toBeInstanceOf(ErroDaApi);
    expect((erro as ErroDaApi).tipo).toBe('resposta');
  });
});
