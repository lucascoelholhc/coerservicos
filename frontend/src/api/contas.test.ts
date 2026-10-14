import { afterEach, describe, expect, it, vi } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { buscarMinhaConta, criarCliente, reenviarConfirmacaoDeEmail } from './contas';

describe('contas', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('criar cliente: POST /api/contas/cliente com os dados e a versão dos termos aceita', async () => {
    const { pedidos } = simularFetch(() => json({ id: 'u1', nome: 'Ana Souza' }, 201));
    const dados = {
      nome: 'Ana Souza',
      celular: '47900000001',
      email: 'ana@exemplo.com',
      cep: '89010000',
      senha: 'Casa-Azul-2026',
      versaoTermosAceita: '1.0',
    };

    await expect(criarCliente(dados)).resolves.toEqual({ id: 'u1', nome: 'Ana Souza' });

    expect(pedidos[0]).toMatchObject({ url: '/api/contas/cliente', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual(dados);
  });

  it('minha conta: GET /api/contas/eu', async () => {
    const { pedidos } = simularFetch(() => json({ id: 'u1' }));

    await expect(buscarMinhaConta(new AbortController().signal)).resolves.toEqual({ id: 'u1' });
    expect(pedidos[0]).toMatchObject({ url: '/api/contas/eu', metodo: 'GET' });
  });

  it('mandar o link de confirmação de novo: POST /api/contas/eu/email/confirmacao', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 202 }));

    await reenviarConfirmacaoDeEmail();

    expect(pedidos[0]).toMatchObject({ url: '/api/contas/eu/email/confirmacao', metodo: 'POST' });
  });
});
