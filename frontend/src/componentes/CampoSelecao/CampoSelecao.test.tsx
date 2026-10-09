import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoSelecao } from './CampoSelecao';

const PROFISSOES = [
  { valor: 'pedreiro', texto: 'Pedreiro' },
  { valor: 'pintor', texto: 'Pintor' },
];

describe('CampoSelecao', () => {
  it('rótulo ligado ao select, opções e seta escondida do leitor de tela', async () => {
    const { container } = render(
      <CampoSelecao id="h-prof" nome="profissao" rotulo="Do que você precisa?" opcoes={PROFISSOES} />,
    );
    const campo = screen.getByRole('combobox', { name: 'Do que você precisa?' });

    expect(campo).toHaveAttribute('name', 'profissao');
    expect(screen.getAllByRole('option')).toHaveLength(2);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    await semViolacoes(container);
  });

  it('escolhe uma opção', async () => {
    render(<CampoSelecao id="h-prof" nome="profissao" rotulo="Do que você precisa?" opcoes={PROFISSOES} />);

    await userEvent.selectOptions(screen.getByRole('combobox'), 'pintor');

    expect(screen.getByRole('combobox')).toHaveValue('pintor');
  });
});
