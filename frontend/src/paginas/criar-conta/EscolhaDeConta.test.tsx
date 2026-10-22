import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { EscolhaDeConta } from './EscolhaDeConta';

describe('/criar-conta', () => {
  it('"Quero contratar" leva ao cadastro de cliente e "Quero trabalhar" à conta de quem vai trabalhar', async () => {
    const { container } = render(
      <MemoryRouter>
        <EscolhaDeConta />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Criar conta' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Quero contratar/ })).toHaveAttribute('href', '/criar-conta/cliente');
    expect(screen.getByRole('link', { name: /Quero trabalhar/ })).toHaveAttribute('href', '/criar-conta/profissional');
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar');
    expect(document.title).toBe('Criar conta · COE Serviços');
    await semViolacoes(container);
  });
});
