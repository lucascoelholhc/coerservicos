import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO } from '../../teste/dadosDaApi';
import { DETALHE_DA_VERIFICACAO, FILA_DA_VERIFICACAO } from '../../teste/dadosDaVerificacao';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { FilaDeVerificacao } from './FilaDeVerificacao';
import { VerificacaoDoCadastro } from './VerificacaoDoCadastro';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso;
  return <p>{`Fui para ${local.pathname}${aviso ? ` · ${aviso}` : ''}`}</p>;
}

function abrir(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <Routes>
        <Route path="/admin/verificacao" element={<FilaDeVerificacao />} />
        <Route path="/admin/verificacao/:id" element={<VerificacaoDoCadastro />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

type Resposta = Response | Promise<Response>;

function simular(opcoes: { fila?: (pedido: Pedido) => Resposta; detalhe?: unknown; decisao?: (pedido: Pedido) => Resposta } = {}) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url.startsWith('/api/admin/verificacao?')) {
      return opcoes.fila ? opcoes.fila(pedido) : json(FILA_DA_VERIFICACAO);
    }
    if (pedido.url.endsWith('/url')) {
      return json({ url: 'https://bucket.exemplo/documento/1.jpg?assinatura', expiraEm: '2026-10-22T12:02:00Z' });
    }
    if (pedido.metodo === 'POST') {
      return opcoes.decisao ? opcoes.decisao(pedido) : json({ id: 'p1', status: 'ativo', nr10: 'nao_tem' });
    }
    return json(opcoes.detalhe ?? DETALHE_DA_VERIFICACAO);
  });
}

const ITENS = [
  /CPF válido/,
  /Documento legível/,
  /Selfie confere/,
  /Fotos parecem trabalhos reais/,
  /Texto do perfil sem contato/,
];

async function marcarTudo() {
  for (const item of ITENS) {
    await userEvent.click(screen.getByRole('checkbox', { name: item }));
  }
}

describe('/admin/verificacao: a fila', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lista os cadastros mais antigos primeiro, com profissão, data e NR-10', async () => {
    simular();
    const { container } = abrir('/admin/verificacao');

    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastros para verificar' })).toBeInTheDocument();
    const [primeiro, segundo] = (await screen.findAllByRole('article')) as [HTMLElement, HTMLElement];
    expect(screen.getAllByRole('article')).toHaveLength(2);
    expect(within(primeiro).getByRole('heading', { name: 'Valdir Schmitt' })).toBeInTheDocument();
    expect(within(primeiro).getByText(/Pedreiro · enviado em 20\/10 às 10:00/)).toBeInTheDocument();
    expect(within(segundo).getByText('Tem certificado de NR-10')).toBeInTheDocument();
    expect(within(primeiro).getByRole('link', { name: 'Analisar o cadastro de Valdir Schmitt' })).toHaveAttribute(
      'href',
      '/admin/verificacao/p1',
    );
    expect(document.title).toBe('Verificação de cadastros · COE Serviços');
    await semViolacoes(container);
  });

  it('fila vazia', async () => {
    simular({ fila: () => json({ itens: [], temMais: false }) });
    abrir('/admin/verificacao');

    expect(await screen.findByText('Nenhum cadastro esperando verificação.')).toBeInTheDocument();
  });

  it('"Ver mais" traz a próxima página', async () => {
    const { pedidos } = simular({
      fila: (pedido) =>
        pedido.url.endsWith('pagina=0')
          ? json({ itens: [FILA_DA_VERIFICACAO.itens[0]], temMais: true })
          : json({ itens: [FILA_DA_VERIFICACAO.itens[1]], temMais: false }),
    });
    abrir('/admin/verificacao');

    await userEvent.click(await screen.findByRole('button', { name: 'Ver mais' }));

    expect(await screen.findByRole('heading', { name: 'Cristiano Weber' })).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: 'Ver mais' })).not.toBeInTheDocument();
    expect(pedidos.filter((p) => p.url.includes('pagina')).map((p) => p.url)).toEqual([
      '/api/admin/verificacao?pagina=0',
      '/api/admin/verificacao?pagina=1',
    ]);
  });

  it('erro ao carregar: mensagem e tentar de novo', async () => {
    simular({ fila: () => problema(500, 'erro-interno', 'x') });
    abrir('/admin/verificacao');

    expect(await screen.findByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
  });

  it('mostra o aviso da decisão que acabou de ser tomada', async () => {
    simular();
    render(
      <MemoryRouter initialEntries={[{ pathname: '/admin/verificacao', state: { aviso: 'Cadastro aprovado.' } }]}>
        <Routes>
          <Route path="/admin/verificacao" element={<FilaDeVerificacao />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Cadastro aprovado.')).toBeInTheDocument();
  });
});

describe('/admin/verificacao/:id: conferir e decidir', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('mostra os dados para conferir, com o CPF completo, e abre o documento pela URL assinada', async () => {
    const { pedidos } = simular();
    const { container } = abrir('/admin/verificacao/p1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Valdir Schmitt' })).toBeInTheDocument();
    expect(screen.getByText('111.444.777-35')).toBeInTheDocument();
    expect(screen.getByText('Pedreiro, Pintor')).toBeInTheDocument();
    expect(screen.getByText('R$ 280,00 · meia R$ 160,00')).toBeInTheDocument();
    expect(screen.getByText('Blumenau · até 20 km · + Gaspar')).toBeInTheDocument();
    expect(screen.getByText(/Chego no horário combinado\./)).toBeInTheDocument();
    await semViolacoes(container);

    await userEvent.click(screen.getByRole('button', { name: 'Ver RG (frente)' }));

    expect(await screen.findByRole('link', { name: 'Abrir RG (frente)' })).toHaveAttribute(
      'href',
      'https://bucket.exemplo/documento/1.jpg?assinatura',
    );
    expect(pedidos.some((p) => p.url === '/api/admin/verificacao/p1/documentos/doc-rg/url')).toBe(true);
  });

  it('aprovar: exige todo o checklist; depois volta para a fila com aviso', async () => {
    const { pedidos } = simular();
    abrir('/admin/verificacao/p1');

    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar e publicar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Marque todos os itens da conferência para aprovar.');
    expect(pedidos.filter((p) => p.metodo === 'POST')).toHaveLength(0);

    await marcarTudo();
    await userEvent.click(screen.getByRole('button', { name: 'Aprovar e publicar' }));

    expect(await screen.findByText('Cadastro aprovado. O profissional já aparece na busca.')).toBeInTheDocument();
    const aprovacao = pedidos.find((p) => p.metodo === 'POST');
    expect(aprovacao?.url).toBe('/api/admin/verificacao/p1/aprovar');
    expect(JSON.parse(aprovacao?.corpo ?? '{}')).toEqual({
      itens: { cpf: true, documento: true, selfie: true, fotos: true, texto: true },
    });
  });

  it('NR-10 em análise: pede a decisão; recusado pede motivo', async () => {
    const { pedidos } = simular({ detalhe: { ...DETALHE_DA_VERIFICACAO, nr10: 'em_analise' } });
    abrir('/admin/verificacao/p1');

    await screen.findByRole('group', { name: 'Certificado de NR-10' });
    await marcarTudo();
    await userEvent.click(screen.getByRole('button', { name: 'Aprovar e publicar' }));
    expect(screen.getByRole('group', { name: 'Certificado de NR-10' })).toHaveAccessibleDescription(
      'Decida o certificado de NR-10.',
    );

    await userEvent.click(screen.getByRole('radio', { name: /Não aceito/ }));
    await userEvent.type(screen.getByRole('textbox', { name: /Por que o certificado não foi aceito/ }), 'Certificado vencido.');
    await userEvent.click(screen.getByRole('button', { name: 'Aprovar e publicar' }));

    await screen.findByText(/Cadastro aprovado/);
    expect(JSON.parse(pedidos.find((p) => p.metodo === 'POST')?.corpo ?? '{}')).toEqual({
      itens: { cpf: true, documento: true, selfie: true, fotos: true, texto: true },
      nr10: 'recusado',
      motivoNr10: 'Certificado vencido.',
    });
  });

  it('pedir correção: exige a mensagem e manda o motivo', async () => {
    const { pedidos } = simular({ decisao: () => json({ id: 'p1', status: 'correcao_pedida', nr10: 'nao_tem' }) });
    abrir('/admin/verificacao/p1');

    await userEvent.click(await screen.findByRole('button', { name: 'Pedir correção' }));
    expect(screen.getByRole('textbox', { name: /Mensagem para o profissional/ })).toHaveAccessibleDescription(
      /Escreva o que o profissional precisa corrigir\./,
    );
    await userEvent.type(screen.getByRole('textbox', { name: /Mensagem para o profissional/ }), 'A selfie está escura.');
    await userEvent.click(screen.getByRole('button', { name: 'Pedir correção' }));

    expect(await screen.findByText('Correção pedida. O profissional recebe a mensagem por SMS.')).toBeInTheDocument();
    expect(pedidos.find((p) => p.metodo === 'POST')?.url).toBe('/api/admin/verificacao/p1/correcao');
  });

  it('recusar pede confirmação antes', async () => {
    const { pedidos } = simular({ decisao: () => json({ id: 'p1', status: 'recusado', nr10: 'nao_tem' }) });
    abrir('/admin/verificacao/p1');

    await userEvent.type(await screen.findByRole('textbox', { name: /Mensagem para o profissional/ }), 'Documento de outra pessoa.');
    await userEvent.click(screen.getByRole('button', { name: 'Recusar cadastro' }));
    expect(screen.getByText('Recusar de vez? Depois disso o cadastro não pode mais ser mudado.')).toBeInTheDocument();
    expect(pedidos.filter((p) => p.metodo === 'POST')).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Sim, recusar' }));

    expect(await screen.findByText('Cadastro recusado. O profissional recebe o motivo por SMS.')).toBeInTheDocument();
    expect(JSON.parse(pedidos.find((p) => p.metodo === 'POST')?.corpo ?? '{}')).toEqual({ motivo: 'Documento de outra pessoa.' });
  });

  it('cadastro já decidido por outra pessoa (409): mensagem do servidor', async () => {
    simular({
      decisao: () => problema(409, 'verificacao-indisponivel', 'Este cadastro não está mais esperando verificação.'),
    });
    abrir('/admin/verificacao/p1');

    await screen.findByRole('heading', { level: 1, name: 'Valdir Schmitt' });
    await marcarTudo();
    await userEvent.click(screen.getByRole('button', { name: 'Aprovar e publicar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Este cadastro não está mais esperando verificação.');
  });

  it('mensagem com contato (422): erro no campo da mensagem', async () => {
    simular({ decisao: () => problema(422, 'texto-com-contato', 'Tire o telefone da mensagem.') });
    abrir('/admin/verificacao/p1');

    await userEvent.type(await screen.findByRole('textbox', { name: /Mensagem para o profissional/ }), 'Liga 47 99999-0000');
    await userEvent.click(screen.getByRole('button', { name: 'Pedir correção' }));

    expect(await screen.findByText('Tire o telefone da mensagem.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Mensagem para o profissional/ })).toHaveAttribute('aria-invalid', 'true');
  });

  it('cadastro que não existe: mensagem de erro', async () => {
    simularFetch((pedido) =>
      pedido.url === '/api/publico/catalogo' ? json(CATALOGO_PADRAO) : problema(404, 'nao-encontrado', 'Cadastro não encontrado.'),
    );
    abrir('/admin/verificacao/zzz');

    expect(await screen.findByRole('alert')).toHaveTextContent('Cadastro não encontrado.');
    expect(screen.getByRole('link', { name: 'Fila de verificação' })).toHaveAttribute('href', '/admin/verificacao');
  });
});
