import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { MinhaConta } from '../../api/tipos';
import { semViolacoes } from '../../teste/axe';
import { CONTA_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { CodigoNoLogin } from './CodigoNoLogin';

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/conta/codigo-no-login']}>
      <CodigoNoLogin />
    </MemoryRouter>,
  );
}

const CONFIRMADO: MinhaConta = { ...CONTA_PADRAO, celularConfirmado: true };
const ENVIADO = 'Enviamos um código por SMS para o seu celular.';
type Responder = (pedido: Pedido) => Response | Promise<Response>;

/** Conta (em sequência, a cada GET) e respostas do código e do /eu/mfa. */
function simular(contas: MinhaConta[], mfa: Responder = () => new Response(null, { status: 204 }), pedir?: Responder) {
  let leituras = 0;
  return simularFetch((pedido) => {
    if (pedido.url === '/api/contas/eu') {
      const conta = contas[Math.min(leituras, contas.length - 1)];
      leituras += 1;
      return json(conta);
    }
    if (pedido.url === '/api/contas/eu/mfa/codigo') {
      return pedir ? pedir(pedido) : json({ mensagem: ENVIADO }, 202);
    }
    return mfa(pedido);
  });
}

async function pedirEConfirmar(botao: string, codigo = '123456') {
  await userEvent.click(await screen.findByRole('button', { name: botao }));
  await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), codigo);
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
}

describe('/conta/codigo-no-login', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('desligado → liga com o código do SMS e mostra o novo estado', async () => {
    const { pedidos } = simular([CONFIRMADO, { ...CONFIRMADO, mfaAtivo: true }]);
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Código no login' })).toBeInTheDocument();
    expect(document.title).toBe('Código no login · COE Serviços');
    expect(await screen.findByText('Agora: desligado')).toBeInTheDocument();
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Ligar código no login' }));
    expect(await screen.findByText(ENVIADO)).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Código no login ligado.')).toBeInTheDocument();
    expect(await screen.findByText('Agora: ligado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desligar código no login' })).toBeInTheDocument();
    const definir = pedidos.find((pedido) => pedido.url === '/api/contas/eu/mfa');
    expect(JSON.parse(definir?.corpo ?? '{}')).toEqual({ ativo: true, codigo: '123456' });
  });

  it('ligado → desliga mandando ativo: false', async () => {
    const { pedidos } = simular([{ ...CONFIRMADO, mfaAtivo: true }, CONFIRMADO]);
    abrir();

    await pedirEConfirmar('Desligar código no login');

    expect(await screen.findByText('Código no login desligado.')).toBeInTheDocument();
    const definir = pedidos.find((pedido) => pedido.url === '/api/contas/eu/mfa');
    expect(JSON.parse(definir?.corpo ?? '{}')).toEqual({ ativo: false, codigo: '123456' });
  });

  it('celular não confirmado: mostra o motivo e o caminho para confirmar, sem botão de ligar', async () => {
    simular([CONTA_PADRAO]);
    abrir();

    expect(await screen.findByText('Para usar o código no login, confirme seu celular primeiro.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Confirmar celular' })).toHaveAttribute('href', '/conta/confirmar-celular');
    expect(screen.queryByRole('button', { name: 'Ligar código no login' })).not.toBeInTheDocument();
  });

  it('ADMIN: obrigatório, sem botão de desligar', async () => {
    simular([{ ...CONFIRMADO, papeis: ['ADMIN'], mfaAtivo: true }]);
    abrir();

    expect(await screen.findByText('Para a equipe da COE, o código por SMS no login é obrigatório.')).toBeInTheDocument();
    expect(screen.getByText('Agora: ligado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /código no login/ })).not.toBeInTheDocument();
  });

  it('422 codigo-invalido: erro no campo do código', async () => {
    simular([CONFIRMADO], () => problema(422, 'codigo-invalido', 'Código incorreto ou vencido. Peça um novo.'));
    abrir();

    await pedirEConfirmar('Ligar código no login', '000000');

    expect(await screen.findByText('Código incorreto ou vencido. Peça um novo.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Código' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('403 ao pedir o código: mensagem da API', async () => {
    simular([CONFIRMADO], undefined, () =>
      problema(403, 'celular-nao-confirmado', 'Confirme o seu celular antes de ligar o código por SMS.'),
    );
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Ligar código no login' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Confirme o seu celular antes de ligar o código por SMS.');
  });

  it('"Cancelar" volta sem mudar nada', async () => {
    const { pedidos } = simular([CONFIRMADO]);
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Ligar código no login' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('button', { name: 'Ligar código no login' })).toBeInTheDocument();
    expect(pedidos.some((pedido) => pedido.url === '/api/contas/eu/mfa')).toBe(false);
  });

  it('carregando e erro com "Tentar de novo"', async () => {
    let tentativas = 0;
    simularFetch(() => {
      tentativas += 1;
      return tentativas === 1 ? problema(500, 'x', 'x') : json(CONFIRMADO);
    });
    abrir();

    expect(screen.getByRole('status')).toHaveTextContent('Carregando…');
    expect(await screen.findByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByText('Agora: desligado')).toBeInTheDocument();
  });
});
