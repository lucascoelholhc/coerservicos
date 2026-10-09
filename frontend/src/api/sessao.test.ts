import { afterEach, describe, expect, it, vi } from 'vitest';

import { avisarSessaoPerdida, encerrarSessao, iniciarSessao, ouvirSessao, tokenAtual, usuarioAtual } from './sessao';

const SESSAO = {
  accessToken: 'token-secreto',
  expiraEm: '2026-10-09T12:15:00Z',
  usuario: { id: 'u1', nome: 'Ana', papeis: ['CLIENTE'], contatoPendente: false },
};

describe('sessão em memória', () => {
  afterEach(() => encerrarSessao());

  it('começa sem token e sem usuário; sessão perdida sem ninguém ouvindo não quebra', () => {
    expect(tokenAtual()).toBeNull();
    expect(usuarioAtual()).toBeNull();
    expect(() => avisarSessaoPerdida()).not.toThrow();
  });

  it('iniciar guarda token e usuário só na memória; encerrar limpa', () => {
    iniciarSessao(SESSAO);

    expect(tokenAtual()).toBe('token-secreto');
    expect(usuarioAtual()).toEqual(SESSAO.usuario);
    expect(JSON.stringify({ ...localStorage })).not.toContain('token-secreto');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('token-secreto');

    encerrarSessao();
    expect(tokenAtual()).toBeNull();
    expect(usuarioAtual()).toBeNull();
  });

  it('avisa quem está ouvindo; quem parou de ouvir não é avisado', () => {
    const ouvinte = vi.fn();
    const parar = ouvirSessao(ouvinte);

    iniciarSessao(SESSAO);
    parar();
    encerrarSessao();

    expect(ouvinte).toHaveBeenCalledOnce();
  });
});
