import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ErroDaApi } from './erros';
import { useRecurso } from './useRecurso';

describe('useRecurso', () => {
  it('carregando e depois pronto com os dados', async () => {
    const buscar = vi.fn(async () => ['a']);
    const { result } = renderHook(() => useRecurso(buscar));

    expect(result.current.situacao).toBe('carregando');
    await waitFor(() => expect(result.current).toMatchObject({ situacao: 'pronto', dados: ['a'] }));
  });

  it('vazio quando a regra de vazio diz que não há nada', async () => {
    const { result } = renderHook(() => useRecurso(async () => [] as string[], (lista) => lista.length === 0));

    await waitFor(() => expect(result.current.situacao).toBe('vazio'));
  });

  it('erro tipado e "tentar de novo" busca outra vez', async () => {
    const buscar = vi
      .fn<(sinal: AbortSignal) => Promise<string>>()
      .mockRejectedValueOnce(new ErroDaApi('servidor', { status: 500 }))
      .mockResolvedValueOnce('ok');
    const { result } = renderHook(() => useRecurso(buscar));

    await waitFor(() => expect(result.current.situacao).toBe('erro'));
    expect(result.current.erro?.tipo).toBe('servidor');

    act(() => result.current.tentarDeNovo());

    expect(result.current.situacao).toBe('carregando');
    await waitFor(() => expect(result.current).toMatchObject({ situacao: 'pronto', dados: 'ok' }));
    expect(buscar).toHaveBeenCalledTimes(2);
  });

  it('regra de vazio que quebra vira erro tipado (não fica carregando para sempre)', async () => {
    const { result } = renderHook(() =>
      useRecurso(
        async () => 'ok',
        () => {
          throw new Error('regra quebrada');
        },
      ),
    );

    await waitFor(() => expect(result.current.erro?.tipo).toBe('servidor'));
  });

  it('falha que não é ErroDaApi também vira erro tipado (servidor)', async () => {
    const { result } = renderHook(() => useRecurso(async () => Promise.reject(new Error('boom'))));

    await waitFor(() => expect(result.current.erro?.tipo).toBe('servidor'));
  });

  it('resposta que chega depois de desmontar é ignorada', async () => {
    let entregar: (valor: string) => void = () => undefined;
    const { result, unmount } = renderHook(() =>
      useRecurso(
        () =>
          new Promise<string>((resolver) => {
            entregar = resolver;
          }),
      ),
    );

    unmount();
    await act(async () => entregar('tarde demais'));

    expect(result.current.situacao).toBe('carregando');
  });

  it('ao desmontar, cancela a busca e não mexe mais no estado', async () => {
    let sinal: AbortSignal | undefined;
    const { result, unmount } = renderHook(() =>
      useRecurso(
        (recebido) =>
          new Promise<string>((_, rejeitar) => {
            sinal = recebido;
            recebido.addEventListener('abort', () => rejeitar(new ErroDaApi('cancelado')));
          }),
      ),
    );

    unmount();

    expect(sinal?.aborted).toBe(true);
    expect(result.current.situacao).toBe('carregando');
  });
});
