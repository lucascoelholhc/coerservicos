import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Opcoes, type Opcao } from './Opcoes';

const PROFISSOES: Opcao[] = [
  { valor: 'pedreiro', texto: 'Pedreiro' },
  { valor: 'pintor', texto: 'Pintor', detalhe: 'Paredes e muros' },
];

function Teste({ tipo, inicial = [] }: { tipo: 'unica' | 'varias'; inicial?: string[] }) {
  const [escolhas, setEscolhas] = useState<string[]>(inicial);
  return (
    <>
      <Opcoes legenda="Profissão" nome="profissao" tipo={tipo} opcoes={PROFISSOES} selecionadas={escolhas} aoMudar={setEscolhas} />
      <output>{escolhas.join(',')}</output>
    </>
  );
}

describe('Opcoes', () => {
  it('única: radios num grupo com legenda; escolher troca a escolha', async () => {
    const { container } = render(<Teste tipo="unica" />);

    expect(screen.getByRole('group', { name: 'Profissão' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: 'Pedreiro' }));
    await userEvent.click(screen.getByRole('radio', { name: /Pintor/ }));

    expect(screen.getByRole('status')).toHaveTextContent('pintor');
    expect(screen.getByRole('radio', { name: /Pintor/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Pedreiro' })).not.toBeChecked();
    await semViolacoes(container);
  });

  it('várias: checkboxes que entram e saem', async () => {
    render(<Teste tipo="varias" inicial={['pedreiro']} />);

    await userEvent.click(screen.getByRole('checkbox', { name: /Pintor/ }));
    expect(screen.getByRole('status')).toHaveTextContent('pedreiro,pintor');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pedreiro' }));
    expect(screen.getByRole('status')).toHaveTextContent('pintor');
  });

  it('o detalhe faz parte do nome; texto curto só na tela, nome inteiro para o leitor de tela', () => {
    render(
      <Opcoes
        legenda="Dias"
        nome="dias"
        tipo="varias"
        forma="dia"
        opcoes={[{ valor: '1', texto: 'Segunda-feira', curto: 'seg' }]}
        selecionadas={[]}
        aoMudar={() => undefined}
      />,
    );

    expect(screen.getByRole('checkbox', { name: 'Segunda-feira' })).toBeInTheDocument();
    expect(screen.getByText('seg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('erro ligado ao grupo', async () => {
    const { container } = render(
      <Opcoes legenda="Profissão" nome="p" tipo="unica" opcoes={PROFISSOES} selecionadas={[]} aoMudar={() => undefined} erro="Escolha o seu trabalho." dica="Só uma." />,
    );

    expect(screen.getByRole('group', { name: 'Profissão' })).toHaveAccessibleDescription('Escolha o seu trabalho. Só uma.');
    await semViolacoes(container);
  });

  it('limite em várias: as não escolhidas ficam desabilitadas ao chegar no máximo', () => {
    render(
      <Opcoes legenda="Outras" nome="o" tipo="varias" opcoes={PROFISSOES} selecionadas={['pedreiro']} aoMudar={() => undefined} maximo={1} />,
    );

    expect(screen.getByRole('checkbox', { name: /Pintor/ })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Pedreiro' })).toBeEnabled();
  });
});
