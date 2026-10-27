import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao } from '../../api/sessao';
import type { DiariaDoProfissional } from '../../api/tipos';
import { menuDeBaixo, menuDeCima, MENU_CLIENTE, MENU_PROFISSIONAL } from '../../layout/navegacao';
import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO, REGRAS_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { CADASTRO_COMPLETO } from '../../teste/dadosDoCadastro';
import { CONTRATO_AGUARDANDO, CONTRATO_PAGO } from '../../teste/dadosDosContratos';
import {
  AGENDA,
  DIARIA_DE_HOJE,
  DIARIA_EM_ANDAMENTO,
  DIARIA_ESPERANDO,
  DIARIA_FUTURA,
  DIARIA_LIBERADA,
  DINHEIRO,
} from '../../teste/dadosDoProfissional';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Agenda } from './Agenda';
import { ContratoDoProfissional } from './ContratoDoProfissional';
import { Dinheiro } from './Dinheiro';
import { Hoje } from './Hoje';
import { MeuPerfil } from './MeuPerfil';
import { horaEmSaoPaulo, saudacao } from './textos';
import { TermineiODia } from './TermineiODia';

function OndeEstou() {
  return <p>{`Fui para ${useLocation().pathname}`}</p>;
}

function abrir(caminho: string) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[caminho]}>
        <Routes>
          <Route path="/profissional" element={<Hoje />} />
          <Route path="/profissional/agenda" element={<Agenda />} />
          <Route path="/profissional/dinheiro" element={<Dinheiro />} />
          <Route path="/profissional/perfil" element={<MeuPerfil />} />
          <Route path="/profissional/contratos/:id" element={<ContratoDoProfissional />} />
          <Route path="/profissional/terminar/:id" element={<TermineiODia />} />
          <Route path="*" element={<OndeEstou />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  );
}

/** API da área do profissional; `diarias` muda conforme as ações (cheguei, terminei). */
function simular(inicial: DiariaDoProfissional[], extra: (pedido: Pedido) => Response | undefined = () => undefined) {
  let diarias = inicial;
  return simularFetch((pedido) => {
    const resposta = extra(pedido);
    if (resposta) {
      return resposta;
    }
    if (pedido.url === '/api/profissional/diarias') {
      return json({ itens: diarias });
    }
    if (pedido.url === '/api/profissional/dinheiro') {
      return json(DINHEIRO);
    }
    if (pedido.url === '/api/profissional/agenda') {
      return json(AGENDA);
    }
    if (pedido.url.startsWith('/api/profissional/agenda/folgas/')) {
      return new Response(null, { status: 204 });
    }
    if (pedido.url === '/api/publico/regras') {
      return json(REGRAS_PADRAO);
    }
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url === '/api/profissional/cadastro') {
      return json({ ...CADASTRO_COMPLETO, status: 'ativo', chavePix: '••••-4471' });
    }
    if (pedido.url === `/api/contratos/${CONTRATO_PAGO.id}`) {
      return json(CONTRATO_PAGO);
    }
    if (pedido.url.endsWith('/cheguei')) {
      diarias = diarias.map((d) => (d.id === DIARIA_DE_HOJE.id ? DIARIA_EM_ANDAMENTO : d));
      return json({ ...DIARIA_EM_ANDAMENTO, liberadaEm: null, liberadaPor: null });
    }
    if (pedido.url === '/api/diarias/fotos') {
      return json({ id: 'foto-1' }, 201);
    }
    if (pedido.url.endsWith('/terminei')) {
      return json({
        ...DIARIA_EM_ANDAMENTO,
        status: 'aguardando',
        autoLiberaEm: '2026-11-20T07:00:00Z',
        liberadaEm: null,
        liberadaPor: null,
      });
    }
    return problema(404, 'nao-encontrado', 'Não encontrado.');
  });
}

describe('Área do profissional (FE-06)', () => {
  beforeEach(() =>
    iniciarSessao({
      ...SESSAO_PADRAO,
      usuario: { ...SESSAO_PADRAO.usuario, nome: 'Valdir Souza', papeis: ['CLIENTE', 'PROFISSIONAL'] },
    }),
  );
  afterEach(() => encerrarSessao());

  it('Hoje: obra do dia com endereço e telefone do contrato; "Cheguei" vira "Terminei o dia"', async () => {
    const { pedidos } = simular([DIARIA_DE_HOJE, DIARIA_ESPERANDO, DIARIA_FUTURA]);
    const { container } = abrir('/profissional');

    expect(await screen.findByRole('heading', { name: 'Valdir' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Obra de Carla M.' })).toBeInTheDocument();
    expect(await screen.findByText(/\(47\) 90000-0099/)).toBeInTheDocument();
    expect(screen.getByText('Esperando o cliente')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s840,00/)).toBeInTheDocument();
    await semViolacoes(container);

    await userEvent.click(screen.getByRole('button', { name: /Cheguei na obra/ }));

    expect(await screen.findByRole('link', { name: /Terminei o dia/ })).toHaveAttribute(
      'href',
      `/profissional/terminar/${DIARIA_DE_HOJE.id}`,
    );
    expect(pedidos.some((p) => p.metodo === 'POST' && p.url === `/api/diarias/${DIARIA_DE_HOJE.id}/cheguei`)).toBe(
      true,
    );
  });

  it('Hoje sem obra: mostra a próxima diária', async () => {
    simular([DIARIA_FUTURA]);
    abrir('/profissional');
    expect(await screen.findByText('Nenhuma obra hoje')).toBeInTheDocument();
    expect(screen.getByText(/Sua próxima diária é/)).toBeInTheDocument();
  });

  it('Hoje com erro da API: mensagem e "Tentar de novo"', async () => {
    simularFetch(() => problema(500, 'erro-interno', 'Falhou.'));
    abrir('/profissional');
    expect(await screen.findByRole('button', { name: /Tentar de novo/ })).toBeInTheDocument();
  });

  it('Terminei o dia: exige foto, manda a foto e o recado e mostra "Dia enviado"', async () => {
    const { pedidos } = simular([DIARIA_EM_ANDAMENTO]);
    const { container } = abrir(`/profissional/terminar/${DIARIA_EM_ANDAMENTO.id}`);

    const enviar = await screen.findByRole('button', { name: /Enviar e terminar o dia/ });
    expect(enviar).toBeDisabled();
    await semViolacoes(container);

    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:foto', revokeObjectURL: () => undefined }));
    const foto = new File(['jpeg'], 'servico.jpg', { type: 'image/jpeg' });
    await userEvent.upload(screen.getByLabelText('Foto do que você fez hoje'), foto);
    expect(await screen.findByText('1 foto enviada.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Deixei tudo limpo' }));
    await userEvent.type(screen.getByLabelText('Ou escreva'), 'Volto na quinta');
    await userEvent.click(screen.getByRole('button', { name: /Enviar e terminar o dia/ }));

    expect(await screen.findByRole('heading', { name: 'Dia enviado' })).toBeInTheDocument();
    const terminou = pedidos.find((p) => p.url.endsWith('/terminei'));
    expect(JSON.parse(terminou?.corpo ?? '{}')).toEqual({ recado: 'Deixei tudo limpo. Volto na quinta' });
  });

  it('Terminei o dia antes do "Cheguei": pede para marcar a chegada', async () => {
    simular([DIARIA_DE_HOJE]);
    abrir(`/profissional/terminar/${DIARIA_DE_HOJE.id}`);
    expect(await screen.findByText(/Marque "Cheguei na obra"/)).toBeInTheDocument();
  });

  it('Agenda: marca folga num dia livre e o dia com diária fica travado', async () => {
    const { pedidos } = simular([DIARIA_FUTURA]);
    const { container } = abrir('/profissional/agenda');

    const ocupado = await screen.findByRole('button', { name: /: Diária$/ });
    expect(ocupado).toBeDisabled();
    const livres = screen.getAllByRole('button', { name: /: Livre$/ });
    await semViolacoes(container);

    const primeiro = livres[0];
    if (!primeiro) {
      throw new Error('sem dia livre na agenda');
    }
    await userEvent.click(primeiro);
    expect(await screen.findByText(/é folga\. Ninguém consegue contratar você nesse dia\./)).toBeInTheDocument();
    expect(primeiro).toHaveAttribute('aria-pressed', 'true');
    expect(pedidos.some((p) => p.metodo === 'PUT' && p.url.startsWith('/api/profissional/agenda/folgas/'))).toBe(true);

    await userEvent.click(primeiro);
    expect(await screen.findByText(/voltou para a agenda/)).toBeInTheDocument();
    expect(pedidos.some((p) => p.metodo === 'DELETE')).toBe(true);
  });

  it('Agenda: folga recusada mostra o erro da API', async () => {
    simular([], (pedido) =>
      pedido.metodo === 'PUT' ? problema(409, 'dia-com-diaria', 'Esse dia já tem uma diária marcada.') : undefined,
    );
    abrir('/profissional/agenda');
    const livres = await screen.findAllByRole('button', { name: /: Livre$/ });
    await userEvent.click(livres[0] as HTMLElement);
    expect(await screen.findByText('Esse dia já tem uma diária marcada.')).toBeInTheDocument();
  });

  it('Dinheiro: liberado e "vai cair" da API, Pix mascarado e o dia a dia', async () => {
    simular([DIARIA_LIBERADA, DIARIA_ESPERANDO]);
    const { container } = abrir('/profissional/dinheiro');

    expect(await screen.findByText('Liberado para você')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s840,00/)).toBeInTheDocument();
    expect(screen.queryByText('Parado em reclamação')).not.toBeInTheDocument();
    expect(screen.getByText('••••-4471')).toBeInTheDocument();
    expect(screen.getByText(/A taxa da COE é paga pelo cliente/)).toBeInTheDocument();
    const lista = screen.getByRole('heading', { name: 'Dia a dia' }).parentElement as HTMLElement;
    expect(within(lista).getAllByRole('listitem')).toHaveLength(2);
    await semViolacoes(container);
  });

  it('Dinheiro: mostra o que está parado em reclamação', async () => {
    simular([], (pedido) =>
      pedido.url === '/api/profissional/dinheiro' ? json({ ...DINHEIRO, emReclamacao: '280.00' }) : undefined,
    );
    abrir('/profissional/dinheiro');
    expect(await screen.findByText('Parado em reclamação')).toBeInTheDocument();
  });

  it('Contrato: antes do pagamento, sem endereço nem telefone', async () => {
    simular([], (pedido) =>
      pedido.url === `/api/contratos/${CONTRATO_PAGO.id}`
        ? json({ ...CONTRATO_AGUARDANDO, endereco: null, contato: null, cep: null })
        : undefined,
    );
    const { container } = abrir(`/profissional/contratos/${CONTRATO_PAGO.id}`);
    expect(await screen.findByText(/aparecem aqui depois que ele pagar/)).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('Contrato pago: telefone do cliente e os dias', async () => {
    simular([]);
    abrir(`/profissional/contratos/${CONTRATO_PAGO.id}`);
    expect(await screen.findByText(/\(47\) 90000-0099/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Valdir Souza' })).toBeInTheDocument();
  });

  it('Meu perfil: o que o cliente vê e o link para o perfil público', async () => {
    simular([]);
    const { container } = abrir('/profissional/perfil');
    expect(await screen.findByRole('link', { name: 'Ver como o cliente vê' })).toHaveAttribute(
      'href',
      `/profissionais/${AGENDA.perfilId}`,
    );
    expect(screen.getByText('Quanto cobro por dia')).toBeInTheDocument();
    await semViolacoes(container);
  });
});

describe('Menu e textos do profissional', () => {
  it('quem é PROFISSIONAL vê o menu do profissional; só CLIENTE, o do cliente', () => {
    expect(menuDeBaixo(['CLIENTE', 'PROFISSIONAL'])).toBe(MENU_PROFISSIONAL);
    expect(menuDeBaixo(['CLIENTE'])).toBe(MENU_CLIENTE);
    expect(menuDeCima(['PROFISSIONAL']).map((i) => i.rotulo)).toEqual([
      'Hoje',
      'Agenda',
      'Dinheiro',
      'Conversas',
      'Perfil',
    ]);
    expect(menuDeCima(undefined).map((i) => i.rotulo)).toContain('Para profissionais');
  });

  it('saudação pela hora de São Paulo', () => {
    expect(saudacao(7)).toBe('Bom dia');
    expect(saudacao(13)).toBe('Boa tarde');
    expect(saudacao(20)).toBe('Boa noite');
    expect(horaEmSaoPaulo(new Date('2026-10-26T12:00:00Z'))).toBe(9);
  });
});
