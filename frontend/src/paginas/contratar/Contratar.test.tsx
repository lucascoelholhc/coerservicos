import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO, REGRAS_PADRAO } from '../../teste/dadosDaApi';
import { PERFIL_DE_EXEMPLO } from '../../teste/dadosDaBusca';
import { CONTRATO_AGUARDANDO } from '../../teste/dadosDosContratos';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Contratar } from './Contratar';

function OndeEstou() {
  return <p>{`Fui para ${useLocation().pathname}`}</p>;
}

function abrir() {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[`/contratar/${PERFIL_DE_EXEMPLO.id}`]}>
        <Routes>
          <Route path="/contratar/:id" element={<Contratar />} />
          <Route path="*" element={<OndeEstou />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  );
}

function simular(criar: (pedido: Pedido) => Response = () => json(CONTRATO_AGUARDANDO, 201)) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url === '/api/publico/regras') {
      return json(REGRAS_PADRAO);
    }
    if (pedido.url.startsWith('/api/publico/profissionais/')) {
      return json(PERFIL_DE_EXEMPLO);
    }
    if (pedido.url === '/api/contratos' && pedido.metodo === 'POST') {
      return criar(pedido);
    }
    return problema(404, 'nao-encontrado', 'Não encontrado.');
  });
}

async function escolherDiasEContinuar() {
  const usuario = userEvent.setup();
  await usuario.click(await screen.findByRole('checkbox', { name: /seg, 26\/10/ }));
  await usuario.click(screen.getByRole('checkbox', { name: /ter, 27\/10/ }));
  await usuario.click(screen.getByRole('button', { name: 'Continuar' }));
  return usuario;
}

async function preencherPedido(usuario: ReturnType<typeof userEvent.setup>) {
  await usuario.click(await screen.findByRole('checkbox', { name: 'Alvenaria' }));
  await usuario.click(screen.getByRole('radio', { name: /Já tenho o material/ }));
  await usuario.type(screen.getByLabelText('CEP'), '89010-000');
  await usuario.type(screen.getByLabelText('Rua'), 'Rua das Palmeiras');
  await usuario.type(screen.getByLabelText('Número'), '120');
}

describe('Contratar (FE-05): dias, pedido e envio para o pagamento', () => {
  it('mostra o nome, os dias livres do perfil e não segue sem dia escolhido', async () => {
    simular();
    const { container } = abrir();

    expect(await screen.findByRole('heading', { name: 'Escolha os dias' })).toBeInTheDocument();
    expect(screen.getByText('Valdir S.')).toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(PERFIL_DE_EXEMPLO.diasLivres.length);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText('Escolha pelo menos um dia.')).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('manda os dias, o pedido e o endereço (cidade do perfil por padrão) e abre o contrato para pagar', async () => {
    const { pedidos } = simular();
    abrir();
    const usuario = await escolherDiasEContinuar();
    expect(await screen.findByRole('heading', { name: 'Conte o que precisa' })).toBeInTheDocument();
    await preencherPedido(usuario);

    await usuario.click(screen.getByRole('button', { name: 'Ver valores e pagar' }));

    expect(await screen.findByText(`Fui para /contratos/${CONTRATO_AGUARDANDO.id}`)).toBeInTheDocument();
    const envio = pedidos.find((p) => p.url === '/api/contratos' && p.metodo === 'POST');
    expect(JSON.parse(envio?.corpo ?? '{}')).toEqual({
      profissionalId: PERFIL_DE_EXEMPLO.id,
      dias: [
        { data: '2026-10-26', tipo: 'inteira' },
        { data: '2026-10-27', tipo: 'inteira' },
      ],
      servicos: ['Alvenaria'],
      material: 'cliente_tem',
      endereco: { cep: '89010000', cidade: 4202404, logradouro: 'Rua das Palmeiras', numero: '120' },
      versaoTermosAceita: REGRAS_PADRAO.versaoTermos,
    });
  });

  it('meia diária: o período escolhido vai em cada dia', async () => {
    const { pedidos } = simular();
    abrir();
    const usuario = userEvent.setup();
    await usuario.click(await screen.findByRole('radio', { name: /Meia diária/ }));
    await escolherDiasEContinuar();
    await preencherPedido(usuario);
    await usuario.click(screen.getByRole('button', { name: 'Ver valores e pagar' }));

    await screen.findByText(/Fui para \/contratos\//);
    const corpo = JSON.parse(pedidos.find((p) => p.metodo === 'POST')?.corpo ?? '{}') as { dias: { tipo: string }[] };
    expect(corpo.dias.map((d) => d.tipo)).toEqual(['meia', 'meia']);
  });

  it('o limite da LC 150 (ou dia ocupado) vem da API e volta para a escolha dos dias', async () => {
    simular(() =>
      problema(
        422,
        'lc150-limite',
        'Pela lei das diaristas, a mesma diarista pode trabalhar para você até 2 dias em 7 dias seguidos.',
      ),
    );
    abrir();
    const usuario = await escolherDiasEContinuar();
    await preencherPedido(usuario);

    await usuario.click(screen.getByRole('button', { name: 'Ver valores e pagar' }));

    expect(await screen.findByRole('heading', { name: 'Escolha os dias' })).toBeInTheDocument();
    expect(screen.getByText(/até 2 dias em 7 dias seguidos/)).toBeInTheDocument();
  });

  it('contato no texto: a mensagem da API aparece no campo e nada some', async () => {
    simular(() =>
      problema(422, 'texto-com-contato', 'Tire o telefone do texto.', { campo: 'descricao' }),
    );
    abrir();
    const usuario = await escolherDiasEContinuar();
    await preencherPedido(usuario);
    await usuario.type(screen.getByLabelText('Explique com suas palavras'), 'me liga 47 99999-0000');

    await usuario.click(screen.getByRole('button', { name: 'Ver valores e pagar' }));

    expect(await screen.findByText('Tire o telefone do texto.')).toBeInTheDocument();
    expect(screen.getByLabelText('Rua')).toHaveValue('Rua das Palmeiras');
  });

  it('erro de um campo que não está na tela (ex.: serviço) aparece como mensagem, nada falha em silêncio', async () => {
    simular(() => problema(422, 'servico-invalido', 'Escolha só serviços que esse profissional faz.', { campo: 'servicos' }));
    abrir();
    const usuario = await escolherDiasEContinuar();
    await preencherPedido(usuario);

    await usuario.click(screen.getByRole('button', { name: 'Ver valores e pagar' }));

    expect(await screen.findByText('Escolha só serviços que esse profissional faz.')).toBeInTheDocument();
  });

});
