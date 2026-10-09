import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { avisarSessaoPerdida, encerrarSessao, iniciarSessao } from './sessao';
import { SessaoProvider, useSessao } from './SessaoProvider';

function OndeEstou() {
  const local = useLocation();
  return <p>Em {`${local.pathname}${local.search}`}</p>;
}

function QuemEsta() {
  const usuario = useSessao();
  return <p>{usuario ? `Olá, ${usuario.nome}` : 'Visitante'}</p>;
}

describe('SessaoProvider', () => {
  afterEach(() => encerrarSessao());

  it('useSessao acompanha a sessão em memória', () => {
    render(
      <MemoryRouter>
        <SessaoProvider>
          <QuemEsta />
        </SessaoProvider>
      </MemoryRouter>,
    );
    expect(screen.getByText('Visitante')).toBeInTheDocument();

    act(() =>
      iniciarSessao({
        accessToken: 't',
        expiraEm: 'x',
        usuario: { id: 'u1', nome: 'Ana', papeis: ['CLIENTE'], contatoPendente: false },
      }),
    );

    expect(screen.getByText('Olá, Ana')).toBeInTheDocument();
  });

  it('sessão perdida leva para /entrar com o caminho atual em voltar', () => {
    render(
      <MemoryRouter initialEntries={['/conta?aba=dados']}>
        <SessaoProvider>
          <Routes>
            <Route path="*" element={<OndeEstou />} />
          </Routes>
        </SessaoProvider>
      </MemoryRouter>,
    );

    act(() => avisarSessaoPerdida());

    expect(screen.getByText('Em /entrar?voltar=%2Fconta%3Faba%3Ddados')).toBeInTheDocument();
  });

  it('depois de desmontar, sessão perdida não navega mais', () => {
    const { unmount } = render(
      <MemoryRouter>
        <SessaoProvider>
          <p>App</p>
        </SessaoProvider>
      </MemoryRouter>,
    );

    unmount();

    expect(() => avisarSessaoPerdida()).not.toThrow();
  });
});
