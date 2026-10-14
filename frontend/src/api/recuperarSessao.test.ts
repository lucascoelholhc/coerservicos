import { afterEach, describe, expect, it, vi } from 'vitest';

import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { json, problema, simularFetch } from '../teste/fetchSimulado';
import { recuperarSessaoAoAbrir, reiniciarRecuperacaoParaTestes } from './recuperarSessao';
import { definirAoPerderSessao, encerrarSessao, situacaoDaSessao, tokenAtual, usuarioAtual } from './sessao';

describe('recuperar a sessão ao abrir o app (cookie HttpOnly do refresh)', () => {
  afterEach(() => {
    encerrarSessao();
    reiniciarRecuperacaoParaTestes();
    vi.unstubAllGlobals();
  });

  it('com cookie válido: fica logado, sem a pessoa fazer nada', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await recuperarSessaoAoAbrir();

    expect(pedidos.map((pedido) => `${pedido.metodo} ${pedido.url}`)).toEqual(['POST /api/auth/renovar']);
    expect(situacaoDaSessao()).toBe('logado');
    expect(tokenAtual()).toBe('token-de-teste');
    expect(usuarioAtual()?.nome).toBe('Ana Souza');
  });

  it.each([
    ['sem cookie (401)', () => problema(401, 'sessao-invalida', 'x')],
    ['servidor fora', () => problema(500, 'x', 'x')],
  ])('%s: visitante, sem mensagem e sem mandar para /entrar', async (_, resposta) => {
    const aoPerder = vi.fn();
    definirAoPerderSessao(aoPerder);
    simularFetch(resposta);

    await recuperarSessaoAoAbrir();

    expect(situacaoDaSessao()).toBe('visitante');
    expect(aoPerder).not.toHaveBeenCalled();
  });

  it('sem internet: visitante', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });

    await recuperarSessaoAoAbrir();

    expect(situacaoDaSessao()).toBe('visitante');
  });

  it('chamada duas vezes (StrictMode monta duas vezes): um só POST /renovar', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));

    await Promise.all([recuperarSessaoAoAbrir(), recuperarSessaoAoAbrir()]);

    expect(pedidos).toHaveLength(1);
  });

  it('enquanto verifica, a situação é "verificando"', async () => {
    let responder: (resposta: Response) => void = () => undefined;
    simularFetch(
      () =>
        new Promise<Response>((resolver) => {
          responder = resolver;
        }),
    );

    const promessa = recuperarSessaoAoAbrir();
    expect(situacaoDaSessao()).toBe('verificando');
    responder(json(SESSAO_PADRAO));
    await promessa;

    expect(situacaoDaSessao()).toBe('logado');
  });
});
