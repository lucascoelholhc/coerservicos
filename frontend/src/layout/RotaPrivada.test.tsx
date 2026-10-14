import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { encerrarSessao, iniciarSessao, iniciarVerificacao } from '../api/sessao';
import { foiSaidaVoluntaria } from '../api/sessao';
import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { semViolacoes } from '../teste/axe';
import { RotaPrivada } from './RotaPrivada';

function OndeEstou() {
  const local = useLocation();
  return <p>Em {`${local.pathname}${local.search}`}</p>;
}

function abrir(papeis?: ('CLIENTE' | 'PROFISSIONAL' | 'ADMIN')[]) {
  return render(
    <MemoryRouter initialEntries={['/conta?aba=dados']}>
      <Routes>
        <Route
          path="/conta"
          element={
            <RotaPrivada papeis={papeis}>
              <h1>Minha conta</h1>
            </RotaPrivada>
          }
        />
        <Route path="/entrar" element={<OndeEstou />} />
        <Route path="/" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RotaPrivada', () => {
  afterEach(() => encerrarSessao());

  it('verificando a sessão: "Carregando…" e nunca pisca a tela de entrar', async () => {
    iniciarVerificacao();
    const { container } = abrir();

    expect(screen.getByRole('status')).toHaveTextContent('Carregando…');
    expect(screen.queryByText(/^Em \/entrar/)).not.toBeInTheDocument();
    await semViolacoes(container);

    act(() => iniciarSessao(SESSAO_PADRAO));
    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
  });

  it('visitante: leva para /entrar com o caminho em voltar', () => {
    abrir();

    expect(screen.getByText('Em /entrar?voltar=%2Fconta%3Faba%3Ddados')).toBeInTheDocument();
  });

  it('logado com o papel certo (ou sem exigência de papel): mostra a página', () => {
    iniciarSessao(SESSAO_PADRAO);
    abrir(['CLIENTE']);

    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
  });

  it('papel errado: "Você não tem acesso a esta página" (não o não encontrado)', async () => {
    iniciarSessao(SESSAO_PADRAO);
    const { container } = abrir(['ADMIN']);

    expect(screen.getByRole('heading', { level: 1, name: 'Você não tem acesso a esta página' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir para minha conta' })).toHaveAttribute('href', '/conta');
    expect(screen.queryByText('Página não encontrada')).not.toBeInTheDocument();
    await semViolacoes(container);
  });

  it('saída voluntária ("Sair"): vai para o Início, não para /entrar; depois disso, volta ao normal', () => {
    iniciarSessao(SESSAO_PADRAO);
    const { unmount } = abrir();
    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();

    act(() => encerrarSessao({ voluntaria: true }));

    expect(screen.getByText('Em /')).toBeInTheDocument();
    expect(foiSaidaVoluntaria()).toBe(false);
    unmount();

    abrir();
    expect(screen.getByText('Em /entrar?voltar=%2Fconta%3Faba%3Ddados')).toBeInTheDocument();
  });
});
