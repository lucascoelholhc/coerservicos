import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO, REGRAS_PADRAO, simularApiPublica } from '../../teste/apiSimulada';
import { json, problema } from '../../teste/fetchSimulado';
import { Inicio } from './Inicio';

function OndeEstou() {
  const local = useLocation();
  return <p>Fui para {`${local.pathname}${local.search}`}</p>;
}

function abrirInicio() {
  return render(
    <MemoryRouter>
      <Routes>
        <Route index element={<Inicio />} />
        <Route path="/busca" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** Espera o catálogo e as regras chegarem. */
async function carregado() {
  await screen.findByRole('link', { name: 'Pintor' });
  await screen.findByText(/A taxa da COE é de/);
}

const NUNCA = () => new Promise<Response>(() => undefined);

describe('Início (visitante) com dados da API', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('título do herói, nome da aba e nenhuma violação de acessibilidade', async () => {
    simularApiPublica();
    const { container } = abrirInicio();
    await carregado();

    expect(
      screen.getByRole('heading', { level: 1, name: 'Pague por dia. Libere quando o dia estiver feito.' }),
    ).toBeInTheDocument();
    expect(document.title).toBe('COE Serviços');
    await semViolacoes(container);
  });

  it('busca com as profissões e as cidades que vieram da API', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();
    const busca = screen.getByRole('search', { name: 'Buscar profissional' });
    const profissao = within(busca).getByRole('combobox', { name: 'Do que você precisa?' });
    const cidade = within(busca).getByRole('combobox', { name: 'Em qual cidade?' });

    expect(within(profissao).getAllByRole('option').map((opcao) => opcao.textContent)).toEqual([
      'Qualquer serviço',
      'Pedreiro',
      'Pintor',
      'Eletricista',
      'Diarista',
      'Jardineiro',
    ]);
    expect(within(cidade).getAllByRole('option')).toHaveLength(CATALOGO_PADRAO.cidades.length + 1);
    expect(within(cidade).getByRole('option', { name: 'Balneário Camboriú' })).toHaveValue('4202008');
  });

  it('cidades de mais de um estado aparecem com a UF', async () => {
    simularApiPublica({
      catalogo: {
        ...CATALOGO_PADRAO,
        cidades: [...CATALOGO_PADRAO.cidades, { codigoIbge: 4106902, nome: 'Curitiba', uf: 'PR' }],
      },
    });
    abrirInicio();
    await carregado();

    expect(screen.getByRole('option', { name: 'Curitiba - PR' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Blumenau - SC' })).toBeInTheDocument();
  });

  it('"Buscar" leva a /busca com o código da profissão e o código IBGE da cidade', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Do que você precisa?' }), 'pintor');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Em qual cidade?' }), '4202404');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByText('Fui para /busca?profissao=pintor&cidade=4202404')).toBeInTheDocument();
  });

  it('"Buscar" sem escolher nada leva a /busca sem filtros', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();

    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByText('Fui para /busca')).toBeInTheDocument();
  });

  it('enquanto carrega: campos e "Buscar" desabilitados, aviso para leitor de tela', () => {
    simularApiPublica({ catalogo: NUNCA, regras: NUNCA });
    abrirInicio();

    expect(screen.getByRole('combobox', { name: 'Do que você precisa?' })).toBeDisabled();
    expect(screen.getByRole('combobox', { name: 'Em qual cidade?' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent('Carregando profissões e cidades…');
    expect(screen.queryByRole('link', { name: 'Pintor' })).not.toBeInTheDocument();
  });

  it('um único botão principal na tela: o "Buscar"', async () => {
    simularApiPublica();
    const { container } = abrirInicio();
    await carregado();
    const principais = container.querySelectorAll('[data-variante="principal"]');

    expect(principais).toHaveLength(1);
    expect(principais[0]).toHaveTextContent('Buscar');
  });

  it('cartela de exemplo: imagem com nome "Exemplo: ..." e "Exemplo" visível', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();
    const cartela = screen.getByRole('img', { name: /^Exemplo: obra de 3 diárias com o Valdir\./ });

    expect(within(cartela).getByText('Exemplo')).toBeVisible();
    expect(within(cartela).getByText('Ilustração')).toBeVisible();
  });

  it('categorias da API, com ilustração; sem preço "a partir de" e sem "em breve"', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();
    const secao = screen.getByRole('region', { name: 'Quem você precisa?' });
    const nomes = ['Ver todos os profissionais', 'Pedreiro', 'Pintor', 'Eletricista', 'Diarista', 'Jardineiro'];

    expect(within(secao).getAllByRole('link')).toHaveLength(nomes.length);
    for (const nome of nomes) {
      expect(within(secao).getByRole('link', { name: nome })).toBeInTheDocument();
    }
    expect(within(secao).getAllByText('Ilustração')).toHaveLength(5);
    expect(within(secao).getByRole('link', { name: 'Pintor' })).toHaveAttribute('href', '/busca?profissao=pintor');
    expect(within(secao).getByRole('link', { name: 'Ver todos os profissionais' })).toHaveAttribute('href', '/busca');
    expect(secao).not.toHaveTextContent(/a partir de/i);
    expect(document.body).not.toHaveTextContent(/em breve/i);
  });

  it('profissão nova, sem desenho, aparece sem a etiqueta "Ilustração"', async () => {
    simularApiPublica({
      catalogo: {
        ...CATALOGO_PADRAO,
        areas: [
          {
            codigo: 'obra',
            nome: 'Obra e reforma',
            profissoes: [{ codigo: 'encanador', nome: 'Encanador', nomePlural: 'Encanadores', icone: 'drop', servicos: [] }],
          },
        ],
      },
    });
    abrirInicio();
    const encanador = await screen.findByRole('link', { name: 'Encanador' });

    expect(within(encanador).queryByText('Ilustração')).not.toBeInTheDocument();
    expect(encanador).toHaveAttribute('href', '/busca?profissao=encanador');
  });

  it('catálogo sem nenhuma profissão: avisa, sem quebrar a busca', async () => {
    simularApiPublica({ catalogo: { areas: [], cidades: CATALOGO_PADRAO.cidades } });
    abrirInicio();

    expect(await screen.findByText('Ainda não há profissionais para mostrar. Volte daqui a pouco.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeEnabled();
  });

  it('catálogo com erro: mensagem e "Tentar de novo" busca outra vez', async () => {
    let tentativas = 0;
    simularApiPublica({
      catalogo: () => {
        tentativas += 1;
        return tentativas === 1 ? problema(500, 'erro-interno', 'x') : json(CATALOGO_PADRAO);
      },
    });
    abrirInicio();

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent('A COE está com um problema agora. Tente de novo daqui a pouco.');
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    // O botão some: o foco vai para o aviso de carregamento, não se perde no <body>
    expect(screen.getByRole('status')).toHaveFocus();

    expect(await screen.findByRole('link', { name: 'Pintor' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sem conexão: "Sem conexão. Confira a internet e tente de novo."', async () => {
    simularApiPublica({
      catalogo: () => {
        throw new TypeError('Failed to fetch');
      },
    });
    abrirInicio();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('regras da API: prazo e taxa paga pelo cliente', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();

    expect(
      screen.getByText('Terminou o dia, você aprova. Se não responder em 12 h, o valor é liberado ao profissional.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Sem resposta do cliente em 12 h, o dinheiro é liberado')).toBeInTheDocument();
    expect(screen.getByText('A taxa da COE é de 10%, paga pelo cliente.')).toBeInTheDocument();
  });

  it('trocar a resposta para 15%, 24 h e taxa do profissional muda o texto', async () => {
    simularApiPublica({ regras: { comissao: '0.1500', prazoLiberacao: 'PT24H', taxaPagaPor: 'PROFISSIONAL' } });
    abrirInicio();

    expect(await screen.findByText('A taxa da COE é de 15%, descontada do valor do profissional.')).toBeInTheDocument();
    expect(
      screen.getByText('Terminou o dia, você aprova. Se não responder em 24 h, o valor é liberado ao profissional.'),
    ).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/12 h|10%/);
  });

  it('quem paga a taxa desconhecido: a frase da taxa não aparece e o prazo fica sem número', async () => {
    simularApiPublica({ regras: { ...REGRAS_PADRAO, taxaPagaPor: 'AMBOS' } });
    abrirInicio();
    await screen.findByRole('link', { name: 'Pintor' });

    expect(
      await screen.findByText(
        'Terminou o dia, você aprova. Se não responder, o valor é liberado ao profissional depois de um prazo.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/A taxa da COE/)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\d+\s*h\b|%/);
  });

  it('seções que os menus e o rodapé abrem por âncora', async () => {
    simularApiPublica();
    const { container } = abrirInicio();
    await carregado();

    expect(container.querySelector('#categorias')).toBeInTheDocument();
    expect(container.querySelector('#como-funciona')).toBeInTheDocument();
    expect(container.querySelector('#para-profissionais')).toBeInTheDocument();
  });

  it('passos de "Como funciona" e convite para profissionais', async () => {
    simularApiPublica();
    abrirInicio();
    await carregado();
    const passos = within(screen.getByRole('region', { name: 'Como funciona' })).getAllByRole('listitem');
    const convite = screen.getByRole('region', { name: 'Cadastre-se grátis e receba garantido' });

    expect(passos).toHaveLength(3);
    expect(within(convite).getByRole('link', { name: 'Quero trabalhar com a COE' })).toHaveAttribute(
      'data-fundo-escuro',
      'true',
    );
  });
});
