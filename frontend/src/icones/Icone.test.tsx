import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Icone, NOMES_DE_ICONE } from './Icone';

describe('Icone (traços do ic() do protótipo)', () => {
  it.each(NOMES_DE_ICONE)('%s: SVG 24x24 de traço, escondido do leitor de tela', (nome) => {
    const { container } = render(<Icone nome={nome} />);
    const svg = container.querySelector('svg');

    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(svg).toHaveAttribute('focusable', 'false');
    expect(svg).toHaveAttribute('viewBox', '0 0 24 24');
    expect(svg).toHaveAttribute('stroke', 'currentColor');
    expect(svg?.childElementCount).toBeGreaterThan(0);
  });

  it('tamanho padrão 22 e tamanho escolhido', () => {
    const { container, rerender } = render(<Icone nome="home" />);
    expect(container.querySelector('svg')).toHaveAttribute('width', '22');

    rerender(<Icone nome="home" tamanho={40} />);
    expect(container.querySelector('svg')).toHaveAttribute('height', '40');
  });
});
