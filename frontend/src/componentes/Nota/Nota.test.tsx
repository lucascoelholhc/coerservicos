import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Nota } from './Nota';

describe('Nota', () => {
  it.each(['info', 'atencao', 'ok', 'erro'] as const)('%s: ícone + texto, sem papel de região viva por padrão', async (tipo) => {
    const { container } = render(<Nota tipo={tipo}>Seus documentos nunca aparecem no perfil.</Nota>);
    const nota = screen.getByText('Seus documentos nunca aparecem no perfil.').closest('[data-tipo]');

    expect(nota).toHaveAttribute('data-tipo', tipo);
    expect(nota).not.toHaveAttribute('role');
    expect(nota?.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    await semViolacoes(container);
  });

  it('anunciar: erro vira alert', () => {
    render(
      <Nota tipo="erro" anunciar>
        Não foi possível pagar.
      </Nota>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível pagar.');
  });

  it.each(['info', 'atencao', 'ok'] as const)('anunciar: %s vira status', (tipo) => {
    render(
      <Nota tipo={tipo} anunciar>
        Pronto.
      </Nota>,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Pronto.');
  });
});
