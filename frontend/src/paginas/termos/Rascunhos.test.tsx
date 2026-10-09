import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Privacidade, Termos } from './Rascunhos';

describe('termos de uso e política de privacidade (rascunho até o lançamento)', () => {
  it.each([
    [Termos, 'Termos de uso'],
    [Privacidade, 'Política de privacidade'],
  ])('%#: título, etiqueta "Rascunho" visível e aviso do texto final', async (Pagina, titulo) => {
    const { container } = render(
      <MemoryRouter>
        <Pagina />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: titulo })).toBeInTheDocument();
    expect(screen.getByText('Rascunho')).toBeVisible();
    expect(screen.getByText(/O texto final será publicado antes do lançamento/)).toBeInTheDocument();
    expect(document.title).toBe(`${titulo} · COE Serviços`);
    await semViolacoes(container);
  });
});
