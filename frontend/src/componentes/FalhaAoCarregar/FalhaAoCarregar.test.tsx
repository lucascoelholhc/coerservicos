import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ErroDaApi } from '../../api/erros';
import { semViolacoes } from '../../teste/axe';
import { FalhaAoCarregar } from './FalhaAoCarregar';

describe('FalhaAoCarregar', () => {
  it('mostra a mensagem do erro, é anunciada e tem "Tentar de novo"', async () => {
    const aoTentarDeNovo = vi.fn();
    const { container } = render(
      <FalhaAoCarregar erro={new ErroDaApi('sem-conexao')} aoTentarDeNovo={aoTentarDeNovo} />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(aoTentarDeNovo).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toHaveAttribute('data-variante', 'contorno');
    await semViolacoes(container);
  });
});
