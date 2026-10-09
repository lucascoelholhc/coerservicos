import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Ilustracao, PROFISSOES_ILUSTRADAS } from './Ilustracao';

describe('Ilustracao', () => {
  it('tem as 5 profissões do lançamento', () => {
    expect(PROFISSOES_ILUSTRADAS).toEqual(['pedreiro', 'pintor', 'eletricista', 'jardineiro', 'diarista']);
  });

  it.each(PROFISSOES_ILUSTRADAS)('%s: imagem decorativa (alt vazio) com a etiqueta "Ilustração" visível', async (profissao) => {
    const { container } = render(<Ilustracao profissao={profissao} />);
    const imagem = container.querySelector('img');

    expect(imagem).toHaveAttribute('alt', '');
    expect(imagem?.getAttribute('src')).toBeTruthy();
    expect(screen.getByText('Ilustração')).toBeVisible();
    expect(screen.getByText('Ilustração')).toHaveAttribute('aria-hidden', 'true');
    await semViolacoes(container);
  });

  it('cada profissão tem um desenho diferente', () => {
    const fontes = PROFISSOES_ILUSTRADAS.map((profissao) => {
      const { container, unmount } = render(<Ilustracao profissao={profissao} />);
      const fonte = container.querySelector('img')?.getAttribute('src');
      unmount();
      return fonte;
    });

    expect(new Set(fontes).size).toBe(5);
  });

  it('formato quadrado ou 4:3', () => {
    const { container, rerender } = render(<Ilustracao profissao="pintor" />);
    expect(container.firstElementChild).toHaveAttribute('data-formato', '4:3');

    rerender(<Ilustracao profissao="pintor" formato="1:1" />);
    expect(container.firstElementChild).toHaveAttribute('data-formato', '1:1');
  });
});
