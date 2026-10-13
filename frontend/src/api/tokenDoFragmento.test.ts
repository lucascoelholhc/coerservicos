import { afterEach, describe, expect, it } from 'vitest';

import { tirarTokenDoFragmento } from './tokenDoFragmento';

function abrirEm(endereco: string, estado: unknown = null) {
  window.history.replaceState(estado, '', endereco);
}

describe('token no fragmento do link (#token=...)', () => {
  afterEach(() => abrirEm('/'));

  it('devolve o token e limpa o fragmento da barra de endereço, mantendo o caminho, a busca e o estado do Router', () => {
    abrirEm('/confirmar-email?origem=email#token=abc123', { idx: 3 });

    expect(tirarTokenDoFragmento()).toBe('abc123');
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/confirmar-email');
    expect(window.location.search).toBe('?origem=email');
    expect(window.history.state).toEqual({ idx: 3 });
  });

  it('token com caracteres codificados é decodificado', () => {
    abrirEm('/redefinir-senha#token=a%2Bb%3D');

    expect(tirarTokenDoFragmento()).toBe('a+b=');
  });

  it.each([['/confirmar-email'], ['/confirmar-email#outra=coisa'], ['/confirmar-email#token=']])(
    'sem token (%s): null, e o fragmento sai do mesmo jeito',
    (endereco) => {
      abrirEm(endereco);

      expect(tirarTokenDoFragmento()).toBeNull();
      expect(window.location.hash).toBe('');
    },
  );
});
