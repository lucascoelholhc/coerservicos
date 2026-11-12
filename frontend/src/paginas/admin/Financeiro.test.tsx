import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Financeiro } from './Financeiro';

const RESUMO = {
  saldos: {
    guardado: '528.00',
    aRepassar: '400.00',
    receita: '40.00',
    aEstornar: '0.00',
    aDevolver: '0.00',
    taxasDoGateway: '0.00',
    noGateway: '968.00',
    emDisputa: '220.00',
  },
  periodo: {
    de: '2026-10-12',
    ate: '2026-11-11',
    recebido: '968.00',
    liberadoAoProfissional: '400.00',
    receita: '40.00',
    reembolsado: '0.00',
    repassado: '200.00',
  },
};

const ALERTAS = {
  itens: [
    {
      id: 'a1',
      tipo: 'repasse-falhou',
      referencia: 'r1',
      detalhe: { motivo: 'chave-invalida' },
      criadoEm: '2026-11-11T22:00:00Z',
      resolvidoEm: null,
    },
  ],
  temMais: false,
};

const REPASSES = {
  itens: [
    {
      id: 'r1',
      diariaId: 'd1',
      profissionalId: 'p1',
      profissional: 'Osni B.',
      valor: '200.00',
      status: 'falhou',
      erro: 'chave-invalida',
      tentativas: 1,
      pixConferido: true,
      criadoEm: '2026-11-11T20:00:00Z',
      enviadoEm: '2026-11-11T20:01:00Z',
      confirmadoEm: null,
    },
    {
      id: 'r2',
      diariaId: 'd2',
      profissionalId: 'p2',
      profissional: 'Valdir S.',
      valor: '250.00',
      status: 'pendente',
      erro: null,
      tentativas: 0,
      pixConferido: false,
      criadoEm: '2026-11-11T21:00:00Z',
      enviadoEm: null,
      confirmadoEm: null,
    },
  ],
  temMais: false,
};

function simular(extra: (pedido: Pedido) => Response | undefined = () => undefined) {
  return simularFetch((pedido) => {
    const resposta = extra(pedido);
    if (resposta) {
      return resposta;
    }
    if (pedido.url.startsWith('/api/admin/financeiro/alertas')) {
      return json(ALERTAS);
    }
    if (pedido.url.startsWith('/api/admin/financeiro/repasses')) {
      return json(REPASSES);
    }
    if (pedido.url.startsWith('/api/admin/profissionais/p2/pix')) {
      return json({ tipo: 'aleatoria', chave: 'chave-do-valdir', nome: 'Valdir Schmitt', versao: 'v1', conferida: false });
    }
    return json(RESUMO);
  });
}

function abrir() {
  return render(
    <MemoryRouter>
      <Financeiro />
    </MemoryRouter>,
  );
}

describe('Financeiro do admin (FE-07)', () => {
  it('mostra os saldos do livro-caixa, os alertas abertos e os repasses, sem violação de acessibilidade', async () => {
    simular();
    const { container } = abrir();

    expect(await screen.findByRole('heading', { name: 'Financeiro' })).toBeInTheDocument();
    const saldos = await screen.findByRole('region', { name: 'Saldos agora' });
    expect(within(saldos).getByText('R$ 528,00')).toBeInTheDocument();
    expect(within(saldos).getByText('R$ 968,00')).toBeInTheDocument();
    expect(screen.getByText(/Repasse com falha/)).toBeInTheDocument();
    expect(screen.getAllByText(/chave Pix inválida/)).toHaveLength(2); // no alerta e no repasse
    expect(screen.getByText('Osni B.')).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('manda de novo o repasse que falhou e resolve o alerta', async () => {
    const usuario = userEvent.setup();
    const feitos: string[] = [];
    simular((pedido) => {
      if (pedido.metodo === 'POST') {
        feitos.push(pedido.url);
        return new Response(null, { status: 204 });
      }
      return undefined;
    });
    abrir();

    await usuario.click(await screen.findByRole('button', { name: 'Mandar de novo o repasse de Osni B.' }));
    expect(await screen.findByText('Repasse de Osni B. vai sair de novo na próxima rodada.')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Marcar alerta como resolvido' }));

    expect(feitos).toContain('/api/admin/financeiro/repasses/r1/tentar-de-novo');
    expect(feitos).toContain('/api/admin/financeiro/alertas/a1/resolver');
  });

  it('confere a chave Pix vista: manda a versão dela; chave trocada no meio mostra o aviso da API', async () => {
    const usuario = userEvent.setup();
    let corpo: unknown = null;
    simular((pedido) => {
      if (pedido.metodo === 'POST' && pedido.url.endsWith('/pix/conferido')) {
        corpo = JSON.parse(pedido.corpo ?? 'null');
        return problema(409, 'pix-mudou', 'A chave Pix mudou ou não existe. Abra a chave de novo antes de conferir.');
      }
      return undefined;
    });
    abrir();

    await usuario.click(await screen.findByRole('button', { name: 'Ver a chave Pix de Valdir S.' }));
    expect(await screen.findByText('chave-do-valdir')).toBeInTheDocument();
    expect(screen.getByText('Valdir Schmitt')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'A chave é dele: conferir' }));

    expect(corpo).toEqual({ versao: 'v1' });
    expect(await screen.findByText(/A chave Pix mudou/)).toBeInTheDocument();
  });

  it('erro ao carregar mostra "Tentar de novo"', async () => {
    simular((pedido) => (pedido.url.startsWith('/api/admin/financeiro?') ? problema(503, 'servico-instavel', 'Fora do ar.') : undefined));
    abrir();

    expect(await screen.findByRole('button', { name: /Tentar de novo/ })).toBeInTheDocument();
  });
});
