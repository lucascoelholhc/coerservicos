import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import type { Contrato } from '../../api/tipos';
import { semViolacoes } from '../../teste/axe';
import {
  CONTRATO_AGUARDANDO,
  CONTRATO_PAGO,
  LISTA_DE_CONTRATOS,
  PAGAMENTO_PIX,
} from '../../teste/dadosDosContratos';
import { json, problema, simularFetch } from '../../teste/fetchSimulado';
import { ContratoDoCliente } from './ContratoDoCliente';
import { MeusContratos } from './MeusContratos';

function OndeEstou() {
  return <p>{`Fui para ${useLocation().pathname}`}</p>;
}

function abrir(caminho: string) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[caminho]}>
        <Routes>
          <Route path="/contratos" element={<MeusContratos />} />
          <Route path="/contratos/:id" element={<ContratoDoCliente />} />
          <Route path="*" element={<OndeEstou />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  );
}

/** O contrato muda de "aguardando" para "pago" quando o pagamento simulado chega (como o webhook faria). */
function simular(inicial: Contrato = CONTRATO_AGUARDANDO) {
  let atual = inicial;
  return simularFetch((pedido) => {
    if (pedido.url === '/api/contratos') {
      return json(LISTA_DE_CONTRATOS);
    }
    if (pedido.url.endsWith('/pagamento/simular')) {
      atual = CONTRATO_PAGO;
      return new Response(null, { status: 204 });
    }
    if (pedido.url.endsWith('/pagamento') && pedido.metodo === 'POST') {
      const metodo = (JSON.parse(pedido.corpo ?? '{}') as { metodo: 'pix' | 'cartao' }).metodo;
      return json(
        metodo === 'pix'
          ? PAGAMENTO_PIX
          : { ...PAGAMENTO_PIX, metodo: 'cartao', pixCopiaECola: null, cobrancaId: 'b2' },
        201,
      );
    }
    if (pedido.url === `/api/contratos/${CONTRATO_AGUARDANDO.id}`) {
      return json(atual);
    }
    return problema(404, 'nao-encontrado', 'Contrato não encontrado.');
  });
}

describe('Minhas diárias (FE-05)', () => {
  it('lista os contratos com o profissional, a situação e o total, e abre o contrato', async () => {
    simular();
    const { container } = abrir('/contratos');

    const item = await screen.findByRole('link', { name: /Valdir S\./ });
    expect(item).toHaveAttribute('href', `/contratos/${CONTRATO_AGUARDANDO.id}`);
    expect(within(item).getByText('Esperando pagamento')).toBeInTheDocument();
    expect(within(item).getByText(/R\$\s616,00/)).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('sem contratos: convite para buscar', async () => {
    simularFetch(() => json({ itens: [] }));
    abrir('/contratos');
    expect(await screen.findByText('Nenhuma contratação ainda')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buscar profissional' })).toBeInTheDocument();
  });
});

describe('Contrato do cliente (FE-05): pagar e ver o contato liberado', () => {
  it('aguardando: mostra os valores da API, gera o Pix e o pagamento simulado libera o contato', async () => {
    const { pedidos } = simular();
    const { container } = abrir(`/contratos/${CONTRATO_AGUARDANDO.id}`);
    const usuario = userEvent.setup();

    expect(await screen.findByRole('heading', { name: /Valdir S\./ })).toBeInTheDocument();
    expect(screen.getByText(/R\$\s560,00/)).toBeInTheDocument();
    expect(screen.getByText(/R\$\s56,00/)).toBeInTheDocument();
    expect(screen.getAllByText(/R\$\s616,00/).length).toBeGreaterThan(0);
    await semViolacoes(container);

    await usuario.click(screen.getByRole('button', { name: 'Gerar o Pix' }));
    expect(await screen.findByLabelText('Código Pix copia e cola')).toHaveValue(PAGAMENTO_PIX.pixCopiaECola);
    expect(screen.getByRole('button', { name: /Copiar/ })).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Simular pagamento (ambiente de teste)' }));

    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(screen.getByText('Valdir Souza')).toBeInTheDocument();
    expect(screen.getByText('(47) 90000-0099')).toBeInTheDocument();
    expect(screen.getAllByText('Paga · guardada')).toHaveLength(2);
    expect(pedidos.filter((p) => p.url.endsWith('/pagamento/simular'))).toHaveLength(1);
  });

  it('cartão: só o link da página de pagamento do gateway (a COE não pede dado de cartão)', async () => {
    simular();
    abrir(`/contratos/${CONTRATO_AGUARDANDO.id}`);
    const usuario = userEvent.setup();

    await usuario.click(await screen.findByRole('radio', { name: /Cartão/ }));
    await usuario.click(screen.getByRole('button', { name: 'Pagar com cartão' }));

    expect(await screen.findByRole('link', { name: /Abrir a página de pagamento/ })).toHaveAttribute(
      'href',
      PAGAMENTO_PIX.linkPagamento,
    );
    expect(screen.queryByLabelText(/número do cartão/i)).not.toBeInTheDocument();
  });

  it('pago: contato, endereço e as diárias com a situação', async () => {
    simular(CONTRATO_PAGO);
    const { container } = abrir(`/contratos/${CONTRATO_AGUARDANDO.id}`);

    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(screen.getByText(/Rua das Palmeiras, 120/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gerar o Pix' })).not.toBeInTheDocument();
    await semViolacoes(container);
  });

  it('expirado: avisa e oferece contratar de novo', async () => {
    simular({ ...CONTRATO_AGUARDANDO, status: 'expirado' });
    abrir(`/contratos/${CONTRATO_AGUARDANDO.id}`);

    expect(await screen.findByText(/O prazo para pagar acabou/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Contratar de novo' })).toBeInTheDocument();
  });

  it('contrato de outro (ou que não existe): a mensagem da API', async () => {
    simular();
    abrir('/contratos/outro');
    expect(await screen.findByText('Contrato não encontrado.')).toBeInTheDocument();
  });
});
