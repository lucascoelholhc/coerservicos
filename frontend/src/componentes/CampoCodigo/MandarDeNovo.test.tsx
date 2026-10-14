import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { MandarDeNovo } from './MandarDeNovo';

describe('MandarDeNovo (link)', () => {
  afterEach(() => vi.useRealTimers());

  it('"Mandar o link de novo" espera com a contagem visível, manda e recomeça', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const aoReenviar = vi.fn(() => Promise.resolve());
    const { container } = render(<MandarDeNovo item="link" aoReenviar={aoReenviar} />);
    await semViolacoes(container);

    const botao = screen.getByRole('button', { name: 'Mandar o link de novo' });
    expect(botao).toBeDisabled();
    expect(screen.getByText('Pode pedir outro link em 60 s.')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(60_000));
    expect(botao).toBeEnabled();

    await act(async () => {
      fireEvent.click(botao);
    });

    expect(aoReenviar).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('status')).toHaveTextContent('Mandamos o link de novo.');
    expect(screen.getByText('Pode pedir outro link em 60 s.')).toBeInTheDocument();
  });
});
