import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CartelaDias, type CartelaDeExemplo } from './CartelaDias';

const EXEMPLO: CartelaDeExemplo = {
  descricao: 'Exemplo: obra de 3 diárias com o Valdir.',
  profissional: 'Valdir Schmitt',
  titulo: 'Valdir Schmitt, pedreiro',
  subtitulo: 'Garagem no Garcia: 3 diárias de R$ 280',
  dias: [
    { semana: 'seg', numero: '28', estilo: 'liberado', carimbo: 'liberado', texto: 'Já está com o Valdir' },
    { semana: 'ter, hoje', numero: '29', estilo: 'aprovar', carimbo: 'aprovado', texto: 'Você viu a foto e aprovou' },
    { semana: 'qua', numero: '30', estilo: 'guardado', carimbo: 'guardado', texto: 'Pago e guardado na COE' },
  ],
  rodape: 'Você pagou as 3 diárias antes.',
};

describe('CartelaDias', () => {
  it('é uma imagem com nome que começa por "Exemplo:"', async () => {
    const { container } = render(<CartelaDias cartela={EXEMPLO} />);

    expect(screen.getByRole('img', { name: EXEMPLO.descricao })).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('o nome acessível precisa começar por "Exemplo:" (o tipo recusa outra descrição)', () => {
    // @ts-expect-error descrição sem "Exemplo:" não compila
    const errada: CartelaDeExemplo = { ...EXEMPLO, descricao: 'Obra do Valdir.' };

    expect(errada.descricao).not.toMatch(/^Exemplo:/);
  });

  it('mostra "Exemplo" e "Ilustração" na tela', () => {
    render(<CartelaDias cartela={EXEMPLO} />);
    const cartela = screen.getByRole('img');

    expect(within(cartela).getByText('Exemplo')).toBeVisible();
    expect(within(cartela).getByText('Ilustração')).toBeVisible();
  });

  it('desenha os 3 dias com o carimbo de cada um; só o dia de aprovar anima', () => {
    const { container } = render(<CartelaDias cartela={EXEMPLO} />);
    const dias = container.querySelectorAll('[data-estilo]');

    expect([...dias].map((dia) => dia.getAttribute('data-estilo'))).toEqual(['liberado', 'aprovar', 'guardado']);
    expect(screen.getByText('Liberado')).not.toHaveAttribute('data-animar');
    expect(screen.getByText('Aprovado')).toHaveAttribute('data-animar', 'true');
    expect(screen.getByText('Guardado')).toHaveAttribute('data-compacto', 'true');
    expect(screen.getByText('Você pagou as 3 diárias antes.')).toBeInTheDocument();
  });
});
