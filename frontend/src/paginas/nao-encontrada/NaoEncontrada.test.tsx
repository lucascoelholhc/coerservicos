import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { NaoEncontrada } from './NaoEncontrada';

describe('NaoEncontrada', () => {
  it('diz em português que a página não existe e leva ao início', async () => {
    const { container } = render(
      <MemoryRouter>
        <NaoEncontrada />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Página não encontrada' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir para o início' })).toHaveAttribute('href', '/');
    expect(document.title).toBe('Página não encontrada · COE Serviços');
    await semViolacoes(container);
  });
});
