import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { Botao, type VarianteDoBotao } from './Botao';

const VARIANTES: VarianteDoBotao[] = ['principal', 'escuro', 'contorno', 'suave', 'perigo', 'sucesso'];

describe('Botao', () => {
  it.each(VARIANTES)('variante %s', (variante) => {
    render(<Botao variante={variante}>Aprovar o dia</Botao>);

    expect(screen.getByRole('button', { name: 'Aprovar o dia' })).toHaveAttribute('data-variante', variante);
  });

  it('padrão: principal, 52 px, type="button" e clica', async () => {
    const aoClicar = vi.fn();
    const { container } = render(<Botao onClick={aoClicar}>Buscar</Botao>);
    const botao = screen.getByRole('button', { name: 'Buscar' });

    expect(botao).toHaveAttribute('type', 'button');
    expect(botao).toHaveAttribute('data-variante', 'principal');
    expect(botao).toHaveAttribute('data-tamanho', 'padrao');
    await userEvent.click(botao);
    expect(aoClicar).toHaveBeenCalledOnce();
    await semViolacoes(container);
  });

  it.each(['sm', 'lg', 'xl'] as const)('tamanho %s', (tamanho) => {
    render(<Botao tamanho={tamanho}>Ok</Botao>);

    expect(screen.getByRole('button')).toHaveAttribute('data-tamanho', tamanho);
  });

  it('com "para" vira link do Router', () => {
    render(
      <MemoryRouter>
        <Botao para="/criar-conta" variante="escuro" tamanho="sm">
          Criar conta
        </Botao>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/criar-conta');
  });

  it('desabilitado não clica e é anunciado', async () => {
    const aoClicar = vi.fn();
    render(
      <Botao desabilitado onClick={aoClicar}>
        Pagar
      </Botao>,
    );
    const botao = screen.getByRole('button', { name: 'Pagar' });

    expect(botao).toBeDisabled();
    await userEvent.click(botao);
    expect(aoClicar).not.toHaveBeenCalled();
  });

  it('sobre fundo escuro fica marcado (o CSS deixa amarelo com texto --tinta)', () => {
    render(
      <Botao variante="escuro" sobreFundoEscuro>
        Quero trabalhar com a COE
      </Botao>,
    );

    expect(screen.getByRole('button')).toHaveAttribute('data-fundo-escuro', 'true');
  });

  it('submit quando pedido', () => {
    render(<Botao type="submit">Buscar</Botao>);

    expect(screen.getByRole('button')).toHaveAttribute('type', 'submit');
  });
});
