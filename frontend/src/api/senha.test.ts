import { afterEach, describe, expect, it, vi } from 'vitest';

import { simularFetch } from '../teste/fetchSimulado';
import { confirmarEmail } from './contas';
import { esqueciSenha, redefinirSenha } from './senha';

describe('senha e confirmação de e-mail', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('esqueci a senha: POST /api/auth/senha/esqueci com o login', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 202 }));

    await esqueciSenha('ana@exemplo.com');

    expect(pedidos[0]).toMatchObject({ url: '/api/auth/senha/esqueci', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ login: 'ana@exemplo.com' });
  });

  it.each([
    [{ token: 'abc', novaSenha: 'Nova-Senha-2026' }],
    [{ celular: '47900000001', codigo: '123456', novaSenha: 'Nova-Senha-2026' }],
  ])('redefinir: POST /api/auth/senha/redefinir com o token ou com celular e código (%#)', async (dados) => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await redefinirSenha(dados);

    expect(pedidos[0]).toMatchObject({ url: '/api/auth/senha/redefinir', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual(dados);
  });

  it('confirmar e-mail: POST /api/contas/email/confirmar com o token no corpo, nunca na URL', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await confirmarEmail('abc123');

    expect(pedidos[0]).toMatchObject({ url: '/api/contas/email/confirmar', metodo: 'POST' });
    expect(pedidos[0]?.url).not.toContain('abc123');
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ token: 'abc123' });
  });
});
