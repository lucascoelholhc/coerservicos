import { afterEach, describe, expect, it, vi } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { entrarComCodigo, pedirCodigoParaEntrar } from './autenticacao';
import { confirmarCelular, definirMfa, pedirCodigoDoCelular, pedirCodigoDoMfa } from './contas';

const corpo = (texto: string | undefined) => JSON.parse(texto ?? '{}') as unknown;

describe('códigos por SMS (entrar, confirmar celular, MFA)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('pedirCodigoParaEntrar: POST /api/auth/codigo com o celular; devolve a mensagem da API', async () => {
    const { pedidos } = simularFetch(() => json({ mensagem: 'Se o celular tiver conta, enviamos um código por SMS.' }, 202));

    await expect(pedirCodigoParaEntrar('47900000001')).resolves.toBe('Se o celular tiver conta, enviamos um código por SMS.');
    expect(pedidos[0]?.url).toBe('/api/auth/codigo');
    expect(pedidos[0]?.metodo).toBe('POST');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ celular: '47900000001' });
  });

  it('sem mensagem no corpo: texto padrão (o mesmo para conta que existe ou não)', async () => {
    simularFetch(() => new Response(null, { status: 202 }));

    await expect(pedirCodigoParaEntrar('47900000001')).resolves.toBe('Se o celular tiver conta, enviamos um código por SMS.');
  });

  it('entrarComCodigo: POST /api/auth/entrar-com-codigo e devolve a sessão', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await expect(entrarComCodigo({ celular: '47900000001', codigo: '123456' })).resolves.toEqual(SESSAO_PADRAO);
    expect(pedidos[0]?.url).toBe('/api/auth/entrar-com-codigo');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ celular: '47900000001', codigo: '123456' });
  });

  it.each([
    ['pedirCodigoDoCelular', pedirCodigoDoCelular, '/api/contas/eu/celular/codigo'],
    ['pedirCodigoDoMfa', pedirCodigoDoMfa, '/api/contas/eu/mfa/codigo'],
  ])('%s: POST sem corpo; devolve a mensagem da API ou a padrão', async (_nome, pedir, url) => {
    const { pedidos } = simularFetch(() =>
      pedidos.length === 1 ? json({ mensagem: 'Enviamos um código.' }, 202) : new Response(null, { status: 202 }),
    );

    await expect(pedir()).resolves.toBe('Enviamos um código.');
    await expect(pedir()).resolves.toBe('Enviamos um código por SMS para o seu celular.');
    expect(pedidos[0]?.url).toBe(url);
    expect(pedidos[0]?.metodo).toBe('POST');
  });

  it('confirmarCelular: POST /api/contas/eu/celular/confirmar com o código', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await confirmarCelular('123456');

    expect(pedidos[0]?.url).toBe('/api/contas/eu/celular/confirmar');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ codigo: '123456' });
  });

  it('definirMfa: POST /api/contas/eu/mfa com ativo e código', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await definirMfa({ ativo: true, codigo: '654321' });

    expect(pedidos[0]?.url).toBe('/api/contas/eu/mfa');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ ativo: true, codigo: '654321' });
  });
});
