import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoSenha } from './CampoSenha';

function Campo({ erro }: { erro?: string }) {
  const [valor, setValor] = useState('');
  return (
    <CampoSenha
      nome="senha"
      rotulo="Senha"
      valor={valor}
      aoMudar={setValor}
      autoComplete="current-password"
      dica="Pelo menos 8 caracteres."
      erro={erro}
    />
  );
}

describe('CampoSenha', () => {
  it('escondida por padrão; "Mostrar senha" mostra e esconde de novo', async () => {
    const { container } = render(<Campo />);
    const campo = screen.getByLabelText('Senha');
    const mostrar = screen.getByRole('button', { name: 'Mostrar senha' });

    expect(campo).toHaveAttribute('type', 'password');
    expect(campo).toHaveAttribute('autocomplete', 'current-password');
    expect(mostrar).toHaveAttribute('aria-pressed', 'false');

    await userEvent.click(mostrar);
    expect(campo).toHaveAttribute('type', 'text');
    expect(mostrar).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(mostrar);
    expect(campo).toHaveAttribute('type', 'password');
    await semViolacoes(container);
  });

  it('dica e erro ligados ao campo', () => {
    render(<Campo erro="Senha curta." />);
    const campo = screen.getByLabelText('Senha');

    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveAccessibleDescription('Senha curta. Pelo menos 8 caracteres.');
  });
});
