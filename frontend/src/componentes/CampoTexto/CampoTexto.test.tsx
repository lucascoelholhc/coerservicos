import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoTexto } from './CampoTexto';

function Campo(props: { erro?: string; dica?: string }) {
  const [valor, setValor] = useState('');
  return <CampoTexto nome="nome" rotulo="Seu nome" valor={valor} aoMudar={setValor} autoComplete="name" {...props} />;
}

describe('CampoTexto', () => {
  it('rótulo ligado ao campo, dica descrita e digitação controlada', async () => {
    const { container } = render(<Campo dica="Como você quer ser chamado." />);
    const campo = screen.getByRole('textbox', { name: 'Seu nome' });

    await userEvent.type(campo, 'Ana');

    expect(campo).toHaveValue('Ana');
    expect(campo).toHaveAttribute('autocomplete', 'name');
    expect(campo).toHaveAccessibleDescription('Como você quer ser chamado.');
    expect(campo).not.toHaveAttribute('aria-invalid');
    await semViolacoes(container);
  });

  it('com erro: aria-invalid e a mensagem descrita ao lado do campo', async () => {
    const { container } = render(<Campo erro="Informe seu nome." dica="Como você quer ser chamado." />);
    const campo = screen.getByRole('textbox', { name: 'Seu nome' });

    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveAccessibleDescription('Informe seu nome. Como você quer ser chamado.');
    expect(screen.getByText('Informe seu nome.')).toBeVisible();
    await semViolacoes(container);
  });

  it('somente leitura: o valor aparece, dá para focar e copiar, mas não muda', async () => {
    const aoMudar = vi.fn();
    render(<CampoTexto nome="email" rotulo="E-mail" valor="ana@exemplo.com" aoMudar={aoMudar} somenteLeitura />);
    const campo = screen.getByRole('textbox', { name: 'E-mail' });

    expect(campo).toHaveAttribute('readonly');
    expect(campo).not.toBeDisabled();
  });
});
