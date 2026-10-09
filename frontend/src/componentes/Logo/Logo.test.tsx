import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Logo, Simbolo } from './Logo';

describe('Logo', () => {
  it('é um link para o início, com nome acessível, símbolo escondido e "COE serviços"', async () => {
    const { container } = render(
      <MemoryRouter>
        <Logo />
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: 'COE Serviços, página inicial' });
    expect(link).toHaveAttribute('href', '/');
    expect(link.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(link).toHaveTextContent('COEserviços');
    await semViolacoes(container);
  });

  it('o símbolo claro (fundo escuro) troca as cores pela classe, não por hex no componente', () => {
    const { container } = render(<Simbolo claro tamanho={26} />);
    const svg = container.querySelector('svg');

    expect(svg).toHaveAttribute('width', '26');
    expect(svg).toHaveAttribute('data-claro', 'true');
    expect(container.innerHTML).not.toMatch(/#[0-9a-f]{3,6}/i);
  });
});
