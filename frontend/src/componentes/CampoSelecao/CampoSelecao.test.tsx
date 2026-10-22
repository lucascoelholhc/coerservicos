import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoSelecao } from './CampoSelecao';

const PROFISSOES = [
  { valor: 'pedreiro', texto: 'Pedreiro' },
  { valor: 'pintor', texto: 'Pintor' },
];

describe('CampoSelecao', () => {
  it('rótulo ligado ao select, opções e seta escondida do leitor de tela', async () => {
    const { container } = render(
      <CampoSelecao nome="profissao" rotulo="Do que você precisa?" opcoes={PROFISSOES} />,
    );
    const campo = screen.getByRole('combobox', { name: 'Do que você precisa?' });

    expect(campo).toHaveAttribute('name', 'profissao');
    expect(screen.getAllByRole('option')).toHaveLength(2);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    await semViolacoes(container);
  });

  it('dois campos na mesma tela têm ids diferentes (rótulo sempre ligado ao campo certo)', () => {
    render(
      <>
        <CampoSelecao nome="a" rotulo="Primeiro" opcoes={PROFISSOES} />
        <CampoSelecao nome="b" rotulo="Segundo" opcoes={PROFISSOES} />
      </>,
    );

    expect(screen.getByRole('combobox', { name: 'Primeiro' }).id).not.toBe(
      screen.getByRole('combobox', { name: 'Segundo' }).id,
    );
  });

  it('desabilitado enquanto as opções carregam', () => {
    render(<CampoSelecao nome="profissao" rotulo="Do que você precisa?" opcoes={PROFISSOES} desabilitado />);

    expect(screen.getByRole('combobox', { name: 'Do que você precisa?' })).toBeDisabled();
  });

  it('escolhe uma opção', async () => {
    render(<CampoSelecao nome="profissao" rotulo="Do que você precisa?" opcoes={PROFISSOES} />);

    await userEvent.selectOptions(screen.getByRole('combobox'), 'pintor');

    expect(screen.getByRole('combobox')).toHaveValue('pintor');
  });
});

describe('CampoSelecao controlado', () => {
  it('valor de quem usa, troca em aoMudar e erro ligado ao campo', async () => {
    const aoMudar = vi.fn();
    render(
      <CampoSelecao
        nome="cidade"
        rotulo="Cidade"
        opcoes={[
          { valor: '', texto: 'Escolha…' },
          { valor: '4202404', texto: 'Blumenau' },
        ]}
        valor=""
        aoMudar={aoMudar}
        erro="Escolha a cidade."
      />,
    );

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Cidade' }), '4202404');

    expect(aoMudar).toHaveBeenCalledWith('4202404');
    expect(screen.getByRole('combobox', { name: 'Cidade' })).toHaveAccessibleDescription('Escolha a cidade.');
    expect(screen.getByRole('combobox', { name: 'Cidade' })).toHaveAttribute('aria-invalid', 'true');
  });
});
