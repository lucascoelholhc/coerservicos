import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoTextoLongo } from './CampoTextoLongo';

function Teste({ erro }: { erro?: string }) {
  const [texto, setTexto] = useState('');
  return <CampoTextoLongo nome="sobre" rotulo="Quer contar mais?" valor={texto} aoMudar={setTexto} maxLength={20} erro={erro} />;
}

describe('CampoTextoLongo', () => {
  it('textarea com rótulo e contador de letras', async () => {
    const { container } = render(<Teste />);
    const campo = screen.getByRole('textbox', { name: 'Quer contar mais?' });

    await userEvent.type(campo, 'Chego cedo');

    expect(campo).toHaveValue('Chego cedo');
    expect(screen.getByText('10 de 20 letras')).toBeInTheDocument();
    expect(campo).toHaveAttribute('maxlength', '20');
    await semViolacoes(container);
  });

  it('erro com aria-invalid e descrição', () => {
    render(<Teste erro="Tire o telefone do texto." />);
    const campo = screen.getByRole('textbox', { name: 'Quer contar mais?' });

    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveAccessibleDescription(/Tire o telefone do texto\./);
  });
});
