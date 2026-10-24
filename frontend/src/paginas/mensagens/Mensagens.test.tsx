import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { PERFIL_DE_EXEMPLO } from '../../teste/dadosDaBusca';
import { CONVERSA, ENVIO_BLOQUEADO, MENSAGENS } from '../../teste/dadosDosContratos';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Conversa } from './Conversa';
import { Conversar } from './Conversar';
import { Mensagens } from './Mensagens';

function OndeEstou() {
  return <p>{`Fui para ${useLocation().pathname}`}</p>;
}

function abrir(caminho: string) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[caminho]}>
        <Routes>
          <Route path="/mensagens" element={<Mensagens />} />
          <Route path="/mensagens/:id" element={<Conversa />} />
          <Route path="/conversar/:perfil" element={<Conversar />} />
          <Route path="*" element={<OndeEstou />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  );
}

function simular(enviar: (pedido: Pedido) => Response = () => json(ENVIO_BLOQUEADO, 201)) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/conversas' && pedido.metodo === 'GET') {
      return json({ itens: [CONVERSA] });
    }
    if (pedido.url === '/api/conversas' && pedido.metodo === 'POST') {
      return json(CONVERSA, 201);
    }
    if (pedido.url.endsWith('/lidas')) {
      return new Response(null, { status: 204 });
    }
    if (pedido.url.endsWith('/mensagens') && pedido.metodo === 'GET') {
      return json({ itens: MENSAGENS });
    }
    if (pedido.url.endsWith('/mensagens') && pedido.metodo === 'POST') {
      return enviar(pedido);
    }
    return problema(404, 'nao-encontrado', 'Conversa não encontrada.');
  });
}

describe('Mensagens (FE-05, chat do DOM-05)', () => {
  it('lista as conversas com quem é, as não lidas e se o contato já foi liberado', async () => {
    simular();
    const { container } = abrir('/mensagens');

    const link = await screen.findByRole('link', { name: /Valdir S\./ });
    expect(link).toHaveAttribute('href', '/mensagens/m1');
    expect(screen.getByText('1 nova')).toBeInTheDocument();
    expect(screen.getByText('Contato oculto até o pagamento')).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('abre a conversa, mostra as mensagens dos dois lados e marca como lidas', async () => {
    const { pedidos } = simular();
    const { container } = abrir('/mensagens/m1');

    expect(await screen.findByText('Bom dia! O muro tem uns 6 metros.')).toBeInTheDocument();
    expect(screen.getByText('Bom dia! Dá para fazer em dois dias.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Valdir S.' })).toBeInTheDocument();
    expect(pedidos.some((p) => p.url.endsWith('/lidas'))).toBe(true);
    await semViolacoes(container);
  });

  it('mensagem com contato: fica com quem mandou, com o motivo e o aviso das tentativas', async () => {
    const { pedidos } = simular();
    abrir('/mensagens/m1');
    const usuario = userEvent.setup();

    await usuario.type(await screen.findByLabelText('Sua mensagem'), 'me liga no 47 99123-4567');
    await usuario.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText(/Não foi entregue/)).toBeInTheDocument();
    expect(screen.getByText(/telefone/)).toBeInTheDocument();
    expect(screen.getByText(/Tentativa 1 de 3/)).toBeInTheDocument();
    expect(screen.getByLabelText('Sua mensagem')).toHaveValue('');
    const envio = pedidos.find((p) => p.url.endsWith('/mensagens') && p.metodo === 'POST');
    expect(JSON.parse(envio?.corpo ?? '{}')).toEqual({ texto: 'me liga no 47 99123-4567' });
  });

  it('mensagem normal entra na conversa', async () => {
    simular(() =>
      json(
        {
          mensagem: {
            id: 'g9',
            minha: true,
            texto: 'Combinado, até segunda.',
            bloqueada: false,
            motivos: [],
            lida: false,
            criadoEm: '2026-10-25T12:10:00Z',
          },
          aviso: null,
        },
        201,
      ),
    );
    abrir('/mensagens/m1');
    const usuario = userEvent.setup();

    await usuario.type(await screen.findByLabelText('Sua mensagem'), 'Combinado, até segunda.');
    await usuario.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Combinado, até segunda.')).toBeInTheDocument();
    expect(screen.queryByText(/Não foi entregue/)).not.toBeInTheDocument();
  });

  it('"Conversar" a partir do perfil: abre a conversa e vai para ela', async () => {
    const { pedidos } = simular();
    abrir(`/conversar/${PERFIL_DE_EXEMPLO.id}`);

    expect(await screen.findByText('Bom dia! O muro tem uns 6 metros.')).toBeInTheDocument();
    const abrirConversa = pedidos.find((p) => p.url === '/api/conversas' && p.metodo === 'POST');
    expect(JSON.parse(abrirConversa?.corpo ?? '{}')).toEqual({ profissionalId: PERFIL_DE_EXEMPLO.id });
  });

  it('enviar sem texto avisa e não chama a API', async () => {
    const { pedidos } = simular();
    abrir('/mensagens/m1');
    const usuario = userEvent.setup();

    await usuario.click(await screen.findByRole('button', { name: 'Enviar' }));

    expect(screen.getByText('Escreva a mensagem antes de enviar.')).toBeInTheDocument();
    expect(pedidos.some((p) => p.url.endsWith('/mensagens') && p.metodo === 'POST')).toBe(false);
  });

});
