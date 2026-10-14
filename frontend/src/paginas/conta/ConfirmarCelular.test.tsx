import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { ConfirmarCelular } from './ConfirmarCelular';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso;
  return <p>{`Fui para ${local.pathname}${aviso ? ` · ${aviso}` : ''}`}</p>;
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/conta/confirmar-celular']}>
      <Routes>
        <Route path="/conta/confirmar-celular" element={<ConfirmarCelular />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

const ENVIADO = 'Enviamos um código por SMS para o seu celular.';
type Responder = (pedido: Pedido) => Response | Promise<Response>;

function simular(confirmar: Responder = () => new Response(null, { status: 204 }), pedir?: Responder) {
  return simularFetch((pedido) =>
    pedido.url === '/api/contas/eu/celular/codigo' ? (pedir ? pedir(pedido) : json({ mensagem: ENVIADO }, 202)) : confirmar(pedido),
  );
}

describe('/conta/confirmar-celular', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('manda o código, confirma e volta para Minha conta com aviso', async () => {
    const { pedidos } = simular();
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Confirmar celular' })).toBeInTheDocument();
    expect(document.title).toBe('Confirmar celular · COE Serviços');
    expect(screen.getByRole('link', { name: 'Voltar para minha conta' })).toHaveAttribute('href', '/conta');
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    expect(await screen.findByText(ENVIADO)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mandar outro código' })).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Fui para /conta · Celular confirmado.')).toBeInTheDocument();
    expect(pedidos[0]?.metodo).toBe('POST');
    expect(pedidos[1]?.url).toBe('/api/contas/eu/celular/confirmar');
    expect(JSON.parse(pedidos[1]?.corpo ?? '{}')).toEqual({ codigo: '123456' });
  });

  it('409 celular-ja-confirmado: volta para Minha conta com a mensagem', async () => {
    simular(undefined, () => problema(409, 'celular-ja-confirmado', 'Seu celular já está confirmado.'));
    abrir();

    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    expect(await screen.findByText('Fui para /conta · Seu celular já está confirmado.')).toBeInTheDocument();
  });

  it('422 codigo-invalido: erro no campo do código', async () => {
    simular(() => problema(422, 'codigo-invalido', 'Código incorreto ou vencido. Peça um novo.'));
    abrir();
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Código incorreto ou vencido. Peça um novo.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Código' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('429 ao pedir: mensagem de espera', async () => {
    simular(undefined, () => problema(429, 'muitas-tentativas', 'x'));
    abrir();

    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('sem conexão ao pedir: mensagem', async () => {
    simular(undefined, () => {
      throw new TypeError('Failed to fetch');
    });
    abrir();

    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('código incompleto: avisa sem chamar a API', async () => {
    const { pedidos } = simular();
    abrir();
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(screen.getByRole('textbox', { name: 'Código' })).toHaveAccessibleDescription('Digite os 6 números do código.');
    expect(pedidos).toHaveLength(1);
  });
});
