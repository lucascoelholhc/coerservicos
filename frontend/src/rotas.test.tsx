import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { Rotas } from './rotas';

function abrirEm(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <Rotas />
    </MemoryRouter>,
  );
}

describe('Rotas', () => {
  it('"/" abre o Início dentro da moldura', () => {
    abrirEm('/');

    expect(
      within(screen.getByRole('main')).getByRole('heading', { level: 1, name: /Pague por dia/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('rota que não existe abre "Página não encontrada" dentro da moldura', () => {
    abrirEm('/nao-existe/de-jeito-nenhum');

    expect(
      within(screen.getByRole('main')).getByRole('heading', { level: 1, name: 'Página não encontrada' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Menu' })).toBeInTheDocument();
  });
});
