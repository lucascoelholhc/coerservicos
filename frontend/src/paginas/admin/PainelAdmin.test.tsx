import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Configuracao } from './Configuracao';
import { FotosPendentes } from './FotosPendentes';

const PENDENCIAS = {
  itens: [
    {
      profissionalId: 'p1',
      nome: 'Osni Beduschi',
      fotos: [{ id: 'f1', servico: 'Corte de grama', legenda: 'Gramado novo' }],
    },
  ],
};

const CONFIGURACAO = {
  itens: [
    {
      chave: 'COMISSAO',
      valor: '0.10',
      tipo: 'decimal',
      descricao: 'Comissão da COE sobre cada diária',
      vigenteDesde: '2026-01-01T03:00:00Z',
    },
  ],
};

function abrir(tela: React.ReactNode) {
  return render(<MemoryRouter>{tela}</MemoryRouter>);
}

describe('Fotos novas de perfis aprovados (FE-07)', () => {
  it('lista as fotos esperando o admin e aprova (a foto some da lista)', async () => {
    const usuario = userEvent.setup();
    const feitos: string[] = [];
    simularFetch((pedido: Pedido) => {
      if (pedido.metodo === 'POST') {
        feitos.push(pedido.url);
        return new Response(null, { status: 204 });
      }
      return json(PENDENCIAS);
    });
    const { container } = abrir(<FotosPendentes />);

    expect(await screen.findByRole('heading', { name: 'Fotos novas para conferir' })).toBeInTheDocument();
    expect(screen.getByText('Osni Beduschi')).toBeInTheDocument();
    expect(screen.getByText('Corte de grama · Gramado novo')).toBeInTheDocument();
    await semViolacoes(container);

    await usuario.click(screen.getByRole('button', { name: 'Aprovar a foto Gramado novo' }));

    expect(feitos).toEqual(['/api/admin/profissionais/p1/fotos/f1/aprovar']);
    expect(await screen.findByText('Foto aprovada. Ela já aparece no perfil.')).toBeInTheDocument();
    expect(screen.queryByText('Corte de grama · Gramado novo')).not.toBeInTheDocument();
  });

  it('recusa a foto: ela continua fora do perfil', async () => {
    const usuario = userEvent.setup();
    const feitos: string[] = [];
    simularFetch((pedido: Pedido) => {
      if (pedido.metodo === 'POST') {
        feitos.push(pedido.url);
        return new Response(null, { status: 204 });
      }
      return json(PENDENCIAS);
    });
    abrir(<FotosPendentes />);

    await usuario.click(await screen.findByRole('button', { name: 'Recusar a foto Gramado novo' }));

    expect(feitos).toEqual(['/api/admin/profissionais/p1/fotos/f1/recusar']);
    expect(await screen.findByText('Foto recusada. Ela continua fora do perfil.')).toBeInTheDocument();
  });

  it('sem pendências mostra o estado vazio', async () => {
    simularFetch(() => json({ itens: [] }));
    abrir(<FotosPendentes />);

    expect(await screen.findByText('Nenhuma foto esperando conferência.')).toBeInTheDocument();
  });
});

describe('Configuração com código SMS (FE-07, RN44g)', () => {
  it('muda a comissão pedindo o código por SMS e mandando o código junto', async () => {
    const usuario = userEvent.setup();
    let mudanca: unknown = null;
    let pediuCodigo = false;
    simularFetch((pedido: Pedido) => {
      if (pedido.url === '/api/admin/equipe/codigo') {
        pediuCodigo = true;
        return new Response(null, { status: 202 });
      }
      if (pedido.metodo === 'PUT') {
        mudanca = JSON.parse(pedido.corpo ?? 'null');
        return json({ ...CONFIGURACAO.itens[0], valor: '0.12' });
      }
      return json(CONFIGURACAO);
    });
    const { container } = abrir(<Configuracao />);

    expect(await screen.findByText('Comissão da COE sobre cada diária')).toBeInTheDocument();
    await semViolacoes(container);
    await usuario.click(screen.getByRole('button', { name: 'Mudar COMISSAO' }));
    await usuario.clear(screen.getByLabelText('Novo valor'));
    await usuario.type(screen.getByLabelText('Novo valor'), '0.12');
    await usuario.click(screen.getByRole('button', { name: 'Mandar código por SMS' }));
    expect(pediuCodigo).toBe(true);
    await usuario.type(await screen.findByLabelText(/Código/), '123456');
    await usuario.click(screen.getByRole('button', { name: 'Salvar a mudança' }));

    expect(mudanca).toEqual({ valor: '0.12', codigo: '123456' });
    expect(await screen.findByText('Mudança salva. Vale para os contratos novos.')).toBeInTheDocument();
  });

  it('código errado mostra a mensagem da API e não muda nada', async () => {
    const usuario = userEvent.setup();
    simularFetch((pedido: Pedido) => {
      if (pedido.url === '/api/admin/equipe/codigo') {
        return new Response(null, { status: 202 });
      }
      if (pedido.metodo === 'PUT') {
        return problema(422, 'codigo-invalido', 'Código incorreto ou vencido. Peça um novo.');
      }
      return json(CONFIGURACAO);
    });
    abrir(<Configuracao />);

    await usuario.click(await screen.findByRole('button', { name: 'Mudar COMISSAO' }));
    await usuario.click(screen.getByRole('button', { name: 'Mandar código por SMS' }));
    await usuario.type(await screen.findByLabelText(/Código/), '000000');
    await usuario.click(screen.getByRole('button', { name: 'Salvar a mudança' }));

    expect(await screen.findByText('Código incorreto ou vencido. Peça um novo.')).toBeInTheDocument();
  });
});
