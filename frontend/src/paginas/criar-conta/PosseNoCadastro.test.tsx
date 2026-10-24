import { act, render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { PosseNoCadastro } from './PosseNoCadastro';

type Responder = (pedido: Pedido) => Response | Promise<Response>;

function simular(respostas: Record<string, Responder>) {
  return simularFetch((pedido) => {
    const responder = respostas[pedido.url];
    return responder ? responder(pedido) : new Response(null, { status: 202 });
  });
}

function abrir(campo: 'celular' | 'email', valor: string) {
  const aoComprovar = vi.fn();
  const aoCancelar = vi.fn();
  const resultado = render(<PosseNoCadastro campo={campo} valor={valor} aoComprovar={aoComprovar} aoCancelar={aoCancelar} />);
  return { ...resultado, aoComprovar, aoCancelar };
}

describe('PosseNoCadastro', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('celular: manda o código sozinho, confirma e entrega o comprovante', async () => {
    const { pedidos } = simular({
      '/api/contas/posse/celular': () => json({ mensagem: 'Enviamos o código.' }, 202),
      '/api/contas/posse/celular/confirmar': () => json({ comprovante: 'comp-cel' }),
    });
    const { container, aoComprovar } = abrir('celular', '47900000001');

    expect(screen.getByRole('heading', { level: 2, name: 'Confirme que este celular é seu' })).toBeInTheDocument();
    expect(await screen.findByText('Enviamos o código.')).toBeInTheDocument();
    await semViolacoes(container);
    await userEvent.type(screen.getByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await vi.waitFor(() => expect(aoComprovar).toHaveBeenCalledWith('comp-cel'));
    expect(pedidos.filter((pedido) => pedido.url === '/api/contas/posse/celular')).toHaveLength(1);
    expect(JSON.parse(pedidos[1]?.corpo ?? '{}')).toEqual({ celular: '47900000001', codigo: '123456' });
  });

  it('celular: código errado vira erro no campo; código incompleto avisa sem chamar a API', async () => {
    const { pedidos } = simular({
      '/api/contas/posse/celular/confirmar': () => problema(422, 'codigo-invalido', 'Código incorreto ou vencido. Peça um novo.'),
    });
    abrir('celular', '47900000001');
    const campo = await screen.findByRole('textbox', { name: 'Código' });

    await userEvent.type(campo, '123');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(campo).toHaveAccessibleDescription('Digite os 6 números do código.');
    expect(pedidos).toHaveLength(1);

    await userEvent.type(campo, '456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(await screen.findByText('Código incorreto ou vencido. Peça um novo.')).toBeInTheDocument();
    expect(campo).toHaveAttribute('aria-invalid', 'true');
  });

  it('celular: outra falha ao confirmar vira aviso geral', async () => {
    simular({ '/api/contas/posse/celular/confirmar': () => problema(429, 'muitas-tentativas', 'x') });
    abrir('celular', '47900000001');

    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('falha ao mandar: aviso e "Tentar de novo"', async () => {
    let tentativas = 0;
    simular({
      '/api/contas/posse/celular': () => {
        tentativas += 1;
        if (tentativas === 1) {
          throw new TypeError('Failed to fetch');
        }
        return json({ mensagem: 'Enviamos o código.' }, 202);
      },
    });
    abrir('celular', '47900000001');

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByText('Enviamos o código.')).toBeInTheDocument();
  });

  it('e-mail: manda o link sozinho e explica que a tela continua quando confirmar', async () => {
    const { pedidos } = simular({});
    const { container } = abrir('email', 'ana@exemplo.com');

    expect(screen.getByRole('heading', { level: 2, name: 'Confirme que este e-mail é seu' })).toBeInTheDocument();
    expect(await screen.findByText(/Enviamos um link para ana@exemplo.com/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mandar o link de novo' })).toBeInTheDocument();
    await semViolacoes(container);
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ email: 'ana@exemplo.com' });

    await userEvent.click(screen.getByRole('button', { name: 'Mandar o link de novo' }));
    expect(await screen.findByText('Mandamos o link de novo.')).toBeInTheDocument();
    expect(pedidos).toHaveLength(2);
  });

  it('e-mail: falha ao mandar o link de novo vira aviso', async () => {
    let tentativas = 0;
    simular({
      '/api/contas/posse/email': () => {
        tentativas += 1;
        return tentativas === 1 ? new Response(null, { status: 202 }) : problema(429, 'muitas-tentativas', 'x');
      },
    });
    abrir('email', 'ana@exemplo.com');

    await userEvent.click(await screen.findByRole('button', { name: 'Mandar o link de novo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('no StrictMode o código é pedido uma vez só', async () => {
    const { pedidos } = simular({ '/api/contas/posse/celular': () => json({ mensagem: 'Enviamos o código.' }, 202) });

    render(
      <StrictMode>
        <PosseNoCadastro campo="celular" valor="47900000001" aoComprovar={vi.fn()} aoCancelar={vi.fn()} />
      </StrictMode>,
    );

    expect(await screen.findByText('Enviamos o código.')).toBeInTheDocument();
    expect(pedidos).toHaveLength(1);
  });

  it('celular: "Mandar outro código" depois da espera pede de novo', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    try {
      const { pedidos } = simular({ '/api/contas/posse/celular': () => json({ mensagem: 'Enviamos o código.' }, 202) });
      abrir('celular', '47900000001');
      await screen.findByText('Enviamos o código.');

      await act(async () => {}); // roda o efeito que liga a contagem antes de adiantar o relógio
      act(() => vi.advanceTimersByTime(60_000));
      await userEvent.click(screen.getByRole('button', { name: 'Mandar outro código' }));

      expect(await screen.findByText('Mandamos outro código.')).toBeInTheDocument();
      expect(pedidos).toHaveLength(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('e-mail: falha ao mandar o link mostra "Tentar de novo"', async () => {
    simular({ '/api/contas/posse/email': () => problema(500, 'x', 'x') });
    abrir('email', 'ana@exemplo.com');

    expect(await screen.findByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
  });

  it('"Cancelar" avisa quem abriu', async () => {
    simular({});
    const { aoCancelar } = abrir('email', 'ana@exemplo.com');

    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(aoCancelar).toHaveBeenCalledTimes(1);
  });
});
