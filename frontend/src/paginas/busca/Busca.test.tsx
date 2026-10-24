import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ProfissionalNaBusca, ResultadoDaBusca } from '../../api/tipos';
import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO } from '../../teste/dadosDaApi';
import { RESULTADO_DA_BUSCA } from '../../teste/dadosDaBusca';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Busca } from './Busca';

function OndeEstou() {
  const local = useLocation();
  return <p>Perfil aberto: {local.pathname}</p>;
}

function abrir(endereco = '/busca') {
  // StrictMode como no main.tsx: monta, desmonta e monta de novo (pega efeito que não se desfaz direito)
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[endereco]}>
        <Routes>
          <Route path="/busca" element={<Busca />} />
          <Route path="/profissionais/:id" element={<OndeEstou />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  );
}

type RespostaDaBusca = (pedido: Pedido) => Response | Promise<Response>;

/** Catálogo real e a busca respondendo pela função dada; devolve só os pedidos da busca. */
function simular(busca: RespostaDaBusca = () => json(RESULTADO_DA_BUSCA)) {
  const { pedidos } = simularFetch((pedido) => {
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url.startsWith('/api/publico/profissionais')) {
      return busca(pedido);
    }
    return problema(404, 'nao-encontrado', 'x');
  });
  return { buscas: () => pedidos.filter((pedido) => pedido.url.startsWith('/api/publico/profissionais')) };
}

const ultimaBusca = (buscas: () => Pedido[]) => buscas().at(-1)?.url;

describe('Busca', () => {
  beforeEach(() => {
    // Sexta, 23/10/2026 em São Paulo
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-23T15:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('lê os filtros da URL, pede a API com eles e mostra os profissionais (sem violação de acessibilidade)', async () => {
    const { buscas } = simular();
    const { container } = abrir('/busca?profissao=pedreiro&cidade=4202404');

    expect(await screen.findByRole('heading', { level: 1, name: 'Pedreiros em Blumenau' })).toBeInTheDocument();
    const cartao = (await screen.findByRole('link', { name: 'Valdir S.' })).closest('article') as HTMLElement;
    expect(within(cartao).getByText('Pedreiro e pintor, 20 anos de experiência')).toBeInTheDocument();
    expect(within(cartao).getByText('R$ 280,00')).toBeInTheDocument();
    expect(within(cartao).getByText('Livre amanhã')).toBeInTheDocument();
    expect(within(cartao).getByText('Documento conferido')).toBeInTheDocument();
    expect(within(cartao).getByText('Novo na COE')).toBeInTheDocument();
    const outro = screen.getByRole('link', { name: 'Cristiano W.' }).closest('article') as HTMLElement;
    expect(within(outro).getByText('NR-10 conferido')).toBeInTheDocument();
    expect(within(outro).getByText('Mora em Brusque, a 23 km de Blumenau')).toBeInTheDocument();
    expect(within(outro).getByText('Sem dia livre nas próximas 4 semanas')).toBeInTheDocument();
    expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?profissao=pedreiro&cidade=4202404&pagina=0');
    expect(screen.getByText('2 profissionais')).toBeInTheDocument();
    expect(document.title).toBe('Buscar profissionais · COE Serviços');
    await semViolacoes(container);
  });

  it('o nome leva ao perfil público', async () => {
    simular();
    abrir();

    await userEvent.click(await screen.findByRole('link', { name: 'Valdir S.' }));

    expect(screen.getByText('Perfil aberto: /profissionais/00000000-0000-0000-0000-0000000d0001')).toBeInTheDocument();
  });

  it('categoria troca a profissão (a escolhida fica marcada) e busca de novo', async () => {
    const { buscas } = simular();
    abrir('/busca?profissao=pedreiro&servico=Alvenaria');
    const pintor = await screen.findByRole('button', { name: 'Pintor' });

    await userEvent.click(pintor);

    await waitFor(() => expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?profissao=pintor&pagina=0'));
    expect(screen.getByRole('button', { name: 'Pintor' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('filtro escolhido vira etiqueta; remover a etiqueta tira o filtro', async () => {
    const { buscas } = simular();
    abrir();
    const filtros = await screen.findByRole('complementary', { name: 'Filtros' });

    await userEvent.selectOptions(within(filtros).getByLabelText('Cidade do serviço'), '4202404');
    await userEvent.click(within(filtros).getByRole('checkbox', { name: 'Aceita meia diária' }));

    await waitFor(() =>
      expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?cidade=4202404&meiaDiaria=true&pagina=0'),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Remover filtro Blumenau' }));

    await waitFor(() => expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?meiaDiaria=true&pagina=0'));
    expect(screen.queryByRole('button', { name: 'Remover filtro Blumenau' })).not.toBeInTheDocument();
  });

  it('dia livre, valor, experiência e ferramentas vão para a API com os nomes dela', async () => {
    const { buscas } = simular();
    abrir();
    const filtros = await screen.findByRole('complementary', { name: 'Filtros' });

    await userEvent.click(within(filtros).getByRole('radio', { name: 'ter, 27/10' }));
    await userEvent.selectOptions(within(filtros).getByLabelText('Diária até'), '250.00');
    await userEvent.click(within(filtros).getByRole('radio', { name: '5 anos ou mais' }));
    await userEvent.click(within(filtros).getByRole('checkbox', { name: 'Leva as próprias ferramentas' }));

    await waitFor(() =>
      expect(ultimaBusca(buscas)).toBe(
        '/api/publico/profissionais?dia=2026-10-27&valorMaximo=250.00&experienciaMinima=5&ferramentas=sim&pagina=0',
      ),
    );
    expect(screen.getByRole('button', { name: 'Remover filtro Livre ter, 27/10' })).toBeInTheDocument();
  });

  it('serviço só aparece com uma profissão escolhida, com os serviços dela', async () => {
    const { buscas } = simular();
    abrir('/busca?profissao=pedreiro');
    const filtros = await screen.findByRole('complementary', { name: 'Filtros' });

    await userEvent.click(within(filtros).getByRole('radio', { name: 'Alvenaria' }));

    await waitFor(() =>
      expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?profissao=pedreiro&servico=Alvenaria&pagina=0'),
    );
  });

  it('ordem escolhida vai para a API', async () => {
    const { buscas } = simular();
    abrir();

    await userEvent.selectOptions(await screen.findByLabelText('Mostrar primeiro'), 'preco');

    await waitFor(() => expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?ordem=preco&pagina=0'));
  });

  it('ninguém com os filtros: explica e "Limpar filtros" tira tudo, menos a profissão', async () => {
    const { buscas } = simular((pedido) =>
      pedido.url.includes('cidade=') ? json({ itens: [], temMais: false }) : json(RESULTADO_DA_BUSCA),
    );
    abrir('/busca?profissao=pedreiro&cidade=4202404&ordem=preco');

    const vazio = (await screen.findByRole('heading', { name: 'Ninguém com esses filtros' })).parentElement as HTMLElement;
    await userEvent.click(within(vazio).getByRole('button', { name: 'Limpar filtros' }));

    await waitFor(() => expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?profissao=pedreiro&ordem=preco&pagina=0'));
    expect(await screen.findByRole('link', { name: 'Valdir S.' })).toBeInTheDocument();
  });

  it('erro da API: mensagem do servidor e "Tentar de novo"', async () => {
    let falhar = true;
    simular(() =>
      falhar ? problema(422, 'dia-invalido', 'Escolha um dia a partir de amanhã.') : json(RESULTADO_DA_BUSCA),
    );
    abrir('/busca?dia=2026-01-01');

    expect(await screen.findByText('Escolha um dia a partir de amanhã.')).toBeInTheDocument();
    falhar = false;
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('link', { name: 'Valdir S.' })).toBeInTheDocument();
  });

  it('carregando: avisa sem pular a tela', async () => {
    simular(() => new Promise<Response>(() => undefined));
    abrir();

    expect(await screen.findByText('Procurando profissionais…')).toBeInTheDocument();
  });

  it('"Ver mais" traz a próxima página no fim da lista', async () => {
    const segunda: ResultadoDaBusca = {
      itens: [{ ...(RESULTADO_DA_BUSCA.itens[0] as ProfissionalNaBusca), id: 'p-3', nome: 'Jair H.' }],
      temMais: false,
    };
    const { buscas } = simular((pedido) =>
      json(pedido.url.endsWith('pagina=1') ? segunda : { ...RESULTADO_DA_BUSCA, temMais: true }),
    );
    abrir();

    expect(await screen.findByText('Mais de 2 profissionais')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ver mais' }));

    expect(await screen.findByRole('link', { name: 'Jair H.' })).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(3);
    expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?pagina=1');
    expect(screen.queryByRole('button', { name: 'Ver mais' })).not.toBeInTheDocument();
  });

  it('no celular, "Filtros" abre a folha e "Ver" fecha mostrando quantos há', async () => {
    simular();
    abrir('/busca?cidade=4202404');
    await screen.findByRole('link', { name: 'Valdir S.' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros (1)' }));
    const folha = screen.getByRole('dialog', { name: 'Filtros' });
    expect(within(folha).getByLabelText('Cidade do serviço')).toHaveValue('4202404');
    await userEvent.click(within(folha).getByRole('button', { name: 'Ver 2 profissionais' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Filtros' })).not.toBeInTheDocument());
  });

  it('a folha continua aberta depois de abrir (o "close" atrasado da montagem dupla não a fecha)', async () => {
    simular();
    abrir();
    await screen.findByRole('link', { name: 'Valdir S.' });

    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(screen.getByRole('dialog', { name: 'Filtros' })).toBeInTheDocument();
  });

  it('clicar no fundo escuro fecha a folha', async () => {
    simular();
    abrir();
    await screen.findByRole('link', { name: 'Valdir S.' });
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));

    await userEvent.click(screen.getByRole('dialog', { name: 'Filtros' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Filtros' })).not.toBeInTheDocument());
  });

  it('"Ver mais" que volta depois de trocar o filtro é descartado (nem itens nem erro na lista nova)', async () => {
    let soltar: (resposta: Response) => void = () => {};
    const { buscas } = simular((pedido) => {
      if (pedido.url.endsWith('pagina=1')) {
        return new Promise<Response>((resolver) => {
          soltar = resolver;
        });
      }
      return json({ ...RESULTADO_DA_BUSCA, temMais: !pedido.url.includes('ordem=preco') });
    });
    abrir();
    await userEvent.click(await screen.findByRole('button', { name: 'Ver mais' }));

    await userEvent.selectOptions(screen.getByLabelText('Mostrar primeiro'), 'preco');
    await waitFor(() => expect(ultimaBusca(buscas)).toBe('/api/publico/profissionais?ordem=preco&pagina=0'));
    await screen.findByText('2 profissionais');
    soltar(json({ itens: [{ ...(RESULTADO_DA_BUSCA.itens[0] as ProfissionalNaBusca), id: 'velho', nome: 'Velho V.' }], temMais: false }));
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(screen.queryByRole('link', { name: 'Velho V.' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(2);
  });

  it('voltar para a mesma busca não traz de volta as páginas extras de antes', async () => {
    const segunda: ResultadoDaBusca = {
      itens: [{ ...(RESULTADO_DA_BUSCA.itens[0] as ProfissionalNaBusca), id: 'p-3', nome: 'Jair H.' }],
      temMais: false,
    };
    simular((pedido) => json(pedido.url.endsWith('pagina=1') ? segunda : { ...RESULTADO_DA_BUSCA, temMais: true }));
    abrir();
    await userEvent.click(await screen.findByRole('button', { name: 'Ver mais' }));
    await screen.findByRole('link', { name: 'Jair H.' });

    await userEvent.selectOptions(screen.getByLabelText('Mostrar primeiro'), 'preco');
    await screen.findByText('Mais de 2 profissionais');
    await userEvent.selectOptions(screen.getByLabelText('Mostrar primeiro'), 'livre');

    await screen.findByText('Mais de 2 profissionais');
    expect(screen.queryByRole('link', { name: 'Jair H.' })).not.toBeInTheDocument();
  });

  it('"Ver mais" que falha avisa e deixa tentar de novo', async () => {
    simular((pedido) =>
      pedido.url.endsWith('pagina=1') ? problema(500, 'erro-interno', 'x') : json({ ...RESULTADO_DA_BUSCA, temMais: true }),
    );
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Ver mais' }));

    expect(await screen.findByText(/A COE está com um problema agora/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver mais' })).toBeEnabled();
  });
});
