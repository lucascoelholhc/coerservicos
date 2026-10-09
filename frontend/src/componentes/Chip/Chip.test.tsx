import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Chip } from './Chip';

describe('Chip', () => {
  it('normal: só o texto, sem botão', async () => {
    const { container } = render(<Chip>Alvenaria</Chip>);

    expect(screen.getByText('Alvenaria')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    await semViolacoes(container);
  });

  it('removível: botão "Remover <nome>" que chama aoRemover', async () => {
    const aoRemover = vi.fn();
    const { container } = render(<Chip aoRemover={aoRemover}>Pintor</Chip>);
    const botao = screen.getByRole('button', { name: 'Remover Pintor' });

    await userEvent.click(botao);
    expect(aoRemover).toHaveBeenCalledOnce();
    expect(botao.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(botao).toHaveAttribute('data-alvo', '44');
    await semViolacoes(container);
  });
});
