import { act, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ErroDaApi } from '../../api/erros';
import { semViolacoes } from '../../teste/axe';
import { CampoCodigo } from './CampoCodigo';

interface Opcoes {
  aoReenviar?: () => Promise<void>;
  espera?: number;
  erro?: string;
  rotulo?: string;
}

function Exemplo(opcoes: Opcoes) {
  const [codigo, setCodigo] = useState('');
  return (
    <>
      <CampoCodigo valor={codigo} aoMudar={setCodigo} {...opcoes} />
      <p data-testid="valor">{`valor:${codigo}`}</p>
    </>
  );
}

const botaoDeReenviar = () => screen.getByRole('button', { name: 'Mandar outro código' });

describe('CampoCodigo', () => {
  afterEach(() => vi.useRealTimers());

  it('teclado numérico e preenchimento pelo SMS; guarda só os 6 primeiros números', async () => {
    const { container } = render(<Exemplo />);
    const campo = screen.getByRole('textbox', { name: 'Código' });

    expect(campo).toHaveAttribute('inputmode', 'numeric');
    expect(campo).toHaveAttribute('autocomplete', 'one-time-code');
    fireEvent.change(campo, { target: { value: '12a 3-4567' } });

    expect(screen.getByTestId('valor')).toHaveTextContent('valor:123456');
    expect(screen.queryByRole('button', { name: 'Mandar outro código' })).not.toBeInTheDocument();
    await semViolacoes(container);
  });

  it('rótulo próprio e erro ligado ao campo', () => {
    render(<Exemplo rotulo="Código do SMS" erro="Código incorreto ou vencido. Peça um novo." />);
    const campo = screen.getByRole('textbox', { name: 'Código do SMS' });

    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveAccessibleDescription(/Código incorreto ou vencido/);
  });

  it('"Mandar outro código" espera 60 s com a contagem visível; depois manda e a contagem recomeça', async () => {
    vi.useFakeTimers();
    const aoReenviar = vi.fn(() => Promise.resolve());
    render(<Exemplo aoReenviar={aoReenviar} />);

    expect(botaoDeReenviar()).toBeDisabled();
    expect(screen.getByText('Pode pedir outro código em 60 s.')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(59_000));
    expect(screen.getByText('Pode pedir outro código em 1 s.')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1_000));
    expect(botaoDeReenviar()).toBeEnabled();
    expect(screen.queryByText(/Pode pedir outro código em/)).not.toBeInTheDocument();

    await act(async () => {
      fireEvent.click(botaoDeReenviar());
    });

    expect(aoReenviar).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('status')).toHaveTextContent('Mandamos outro código.');
    expect(screen.getByText('Pode pedir outro código em 60 s.')).toBeInTheDocument();
    expect(botaoDeReenviar()).toBeDisabled();
  });

  it('espera própria (em segundos)', () => {
    vi.useFakeTimers();
    render(<Exemplo aoReenviar={() => Promise.resolve()} espera={5} />);

    expect(screen.getByText('Pode pedir outro código em 5 s.')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(5_000));
    expect(botaoDeReenviar()).toBeEnabled();
  });

  it('enquanto manda: "Mandando…" desabilitado', async () => {
    vi.useFakeTimers();
    render(<Exemplo aoReenviar={() => new Promise<void>(() => undefined)} espera={1} />);
    act(() => vi.advanceTimersByTime(1_000));

    await act(async () => {
      fireEvent.click(botaoDeReenviar());
    });

    expect(screen.getByRole('button', { name: 'Mandando…' })).toBeDisabled();
  });

  it('falha ao mandar (ex.: 429): mensagem da API e o botão volta', async () => {
    vi.useFakeTimers();
    const aoReenviar = vi.fn(() => Promise.reject(new ErroDaApi('limite', { status: 429 })));
    render(<Exemplo aoReenviar={aoReenviar} espera={1} />);
    act(() => vi.advanceTimersByTime(1_000));

    await act(async () => {
      fireEvent.click(botaoDeReenviar());
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Muitas tentativas seguidas. Espere um pouco e tente de novo.');
    expect(botaoDeReenviar()).toBeEnabled();
  });

  it('falha desconhecida: mensagem genérica, nunca o erro cru', async () => {
    vi.useFakeTimers();
    render(<Exemplo aoReenviar={() => Promise.reject(new Error('boom'))} espera={1} />);
    act(() => vi.advanceTimersByTime(1_000));

    await act(async () => {
      fireEvent.click(botaoDeReenviar());
    });

    expect(screen.getByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });
});
