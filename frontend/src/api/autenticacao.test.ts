import { afterEach, describe, expect, it, vi } from 'vitest';

import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { json, simularFetch } from '../teste/fetchSimulado';
import { entrar, sair, sairDeTodos, segundoPasso } from './autenticacao';

describe('autenticação', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('entrar: POST /api/auth/entrar com login e senha', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await expect(entrar({ login: 'ana@exemplo.com', senha: 'Casa-Azul-2026' })).resolves.toEqual(SESSAO_PADRAO);

    expect(pedidos[0]).toMatchObject({ url: '/api/auth/entrar', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ login: 'ana@exemplo.com', senha: 'Casa-Azul-2026' });
  });

  it('segundo passo: POST /api/auth/segundo-passo com o desafio e o código', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await expect(segundoPasso({ desafioId: 'd-1', codigo: '123456' })).resolves.toEqual(SESSAO_PADRAO);

    expect(pedidos[0]).toMatchObject({ url: '/api/auth/segundo-passo', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ desafioId: 'd-1', codigo: '123456' });
  });

  it('sair deste aparelho e de todos: POST /api/auth/sair e /api/auth/sair-de-todos', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await sair();
    await sairDeTodos();

    expect(pedidos.map((pedido) => `${pedido.metodo} ${pedido.url}`)).toEqual([
      'POST /api/auth/sair',
      'POST /api/auth/sair-de-todos',
    ]);
  });
});
