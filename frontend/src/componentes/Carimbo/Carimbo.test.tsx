import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Carimbo } from './Carimbo';

describe('Carimbo', () => {
  it.each([
    ['guardado', 'Guardado', 'marca'],
    ['aprovado', 'Aprovado', 'marca'],
    ['liberado', 'Liberado', 'ok'],
    ['recusado', 'Recusado', 'bad'],
  ] as const)('%s: texto real "%s" (a caixa alta é só do CSS), tom %s', (tipo, texto, tom) => {
    render(<Carimbo tipo={tipo} />);
    const carimbo = screen.getByText(texto);

    expect(carimbo).toHaveAttribute('data-tom', tom);
    expect(carimbo.tagName).toBe('SPAN');
  });

  it('anima uma vez só quando pedido (o reduced-motion desliga no CSS)', () => {
    const { rerender } = render(<Carimbo tipo="aprovado" />);
    expect(screen.getByText('Aprovado')).not.toHaveAttribute('data-animar');

    rerender(<Carimbo tipo="aprovado" animar />);
    expect(screen.getByText('Aprovado')).toHaveAttribute('data-animar', 'true');
  });
});
