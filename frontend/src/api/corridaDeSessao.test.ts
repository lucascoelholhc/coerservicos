import { afterEach, describe, expect, it, vi } from 'vitest';

import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { json, simularFetch } from '../teste/fetchSimulado';
import { renovarSessao } from './cliente';
import { encerrarSessao, iniciarSessao, tokenAtual } from './sessao';

describe('corrida entre a renovação ao abrir e um login novo', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('a resposta atrasada da renovação não troca a sessão de quem acabou de entrar', async () => {
    let responder: (resposta: Response) => void = () => undefined;
    simularFetch(
      () =>
        new Promise<Response>((resolver) => {
          responder = resolver;
        }),
    );
    const renovacao = renovarSessao();

    iniciarSessao({ ...SESSAO_PADRAO, accessToken: 'token-do-login', usuario: { ...SESSAO_PADRAO.usuario, id: 'u-bia' } });
    responder(json(SESSAO_PADRAO));

    await expect(renovacao).resolves.toBe('descartada');
    expect(tokenAtual()).toBe('token-do-login');
  });
});
