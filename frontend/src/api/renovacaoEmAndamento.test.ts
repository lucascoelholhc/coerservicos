import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { json, problema, simularFetch } from '../teste/fetchSimulado';
import { renovarSessao } from './cliente';
import { definirAoPerderSessao, encerrarSessao, tokenAtual } from './sessao';

const USUARIO = { id: 'u1', nome: 'Ana', papeis: ['CLIENTE'], contatoPendente: false };

/**
 * Várias abas abrindo juntas (CORE-07): a aba que sai com o cookie antigo logo depois de outra renovar
 * recebe 409 renovacao-em-andamento. O navegador já tem o cookie novo: uma nova tentativa, uma só,
 * depois de 300 ms.
 */
describe('renovação em andamento em outra aba (409)', () => {
  const aoPerderSessao = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    definirAoPerderSessao(aoPerderSessao);
  });

  afterEach(() => {
    encerrarSessao();
    aoPerderSessao.mockReset();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function renovacoes(pedidos: { url: string }[]) {
    return pedidos.filter((pedido) => pedido.url === '/api/auth/renovar');
  }

  it('409 e depois 200: tenta de novo uma vez, 300 ms depois, e fica com sessão', async () => {
    let vez = 0;
    const { pedidos } = simularFetch(() => {
      vez += 1;
      return vez === 1
        ? problema(409, 'renovacao-em-andamento', 'Outra aba acabou de renovar a sessão.')
        : json({ accessToken: 'da-aba-2', expiraEm: '2026-10-14T12:15:00Z', usuario: USUARIO });
    });

    const resultado = renovarSessao();
    await vi.advanceTimersByTimeAsync(299);
    expect(renovacoes(pedidos)).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);

    await expect(resultado).resolves.toBe('renovada');
    expect(renovacoes(pedidos)).toHaveLength(2);
    expect(tokenAtual()).toBe('da-aba-2');
  });

  it('409 duas vezes: sem sessão nesta aba, sem terceira tentativa e sem aviso de erro', async () => {
    const { pedidos } = simularFetch(() => problema(409, 'renovacao-em-andamento', 'x'));

    const resultado = renovarSessao();
    await vi.advanceTimersByTimeAsync(300);

    await expect(resultado).resolves.toBe('recusada');
    await vi.advanceTimersByTimeAsync(5_000);
    expect(renovacoes(pedidos)).toHaveLength(2);
    expect(tokenAtual()).toBeNull();
    expect(aoPerderSessao).not.toHaveBeenCalled();
  });
});
