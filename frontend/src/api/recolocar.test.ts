import { afterEach, describe, expect, it, vi } from 'vitest';

import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { json, problema, simularFetch } from '../teste/fetchSimulado';
import { atualizarSessao } from './cliente';
import { cadastrarCelularNovo, cadastrarEmailNovo } from './contas';
import { ErroDaApi } from './erros';
import { encerrarSessao, iniciarSessao, usuarioAtual } from './sessao';

const PENDENTE = { ...SESSAO_PADRAO, usuario: { ...SESSAO_PADRAO.usuario, contatoPendente: true } };

describe('recolocar o contato (RN61)', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('cadastrarEmailNovo: PUT /api/contas/eu/email; mensagem da API ou a padrão', async () => {
    const { pedidos } = simularFetch(() =>
      pedidos.length === 1 ? json({ mensagem: 'Link enviado.' }, 202) : new Response(null, { status: 202 }),
    );

    await expect(cadastrarEmailNovo('nova@exemplo.com')).resolves.toBe('Link enviado.');
    await expect(cadastrarEmailNovo('nova@exemplo.com')).resolves.toBe('Enviamos um link para o seu e-mail.');
    expect(pedidos[0]).toMatchObject({ url: '/api/contas/eu/email', metodo: 'PUT' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ email: 'nova@exemplo.com' });
  });

  it('cadastrarCelularNovo: PUT /api/contas/eu/celular; mensagem padrão', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 202 }));

    await expect(cadastrarCelularNovo('47900000009')).resolves.toBe('Enviamos um código por SMS para o seu celular.');
    expect(pedidos[0]).toMatchObject({ url: '/api/contas/eu/celular', metodo: 'PUT' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ celular: '47900000009' });
  });

  it('atualizarSessao: renova agora e a sessão passa a vir sem contato pendente', async () => {
    iniciarSessao(PENDENTE);
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await atualizarSessao();

    expect(pedidos[0]).toMatchObject({ url: '/api/auth/renovar', metodo: 'POST' });
    expect(usuarioAtual()?.contatoPendente).toBe(false);
  });

  it('atualizarSessao: renovação recusada vira erro de sessão', async () => {
    iniciarSessao(PENDENTE);
    simularFetch(() => problema(401, 'sessao-expirada', 'Entre de novo.'));

    const erro = await atualizarSessao().catch((falha: unknown) => falha);

    expect((erro as ErroDaApi).tipo).toBe('sessao');
  });
});
