import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
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
        <Route path="/buscar" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Início (visitante)', () => {
  it('título do herói, nome da aba e nenhuma violação de acessibilidade', async () => {
    const { container } = abrirInicio();

    expect(
      screen.getByRole('heading', { level: 1, name: 'Pague por dia. Libere quando o dia estiver feito.' }),
    ).toBeInTheDocument();
    expect(document.title).toBe('COE Serviços');
    await semViolacoes(container);
  });

  it('busca com profissão e cidade: as 5 profissões e as 10 cidades do lançamento', () => {
    abrirInicio();
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
    expect(within(cidade).getAllByRole('option')).toHaveLength(11);
    expect(within(cidade).getByRole('option', { name: 'Balneário Camboriú' })).toBeInTheDocument();
  });

  it('"Buscar" leva à busca com os filtros escolhidos', async () => {
    abrirInicio();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Do que você precisa?' }), 'pintor');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Em qual cidade?' }), 'Blumenau');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByText('Fui para /buscar?profissao=pintor&cidade=Blumenau')).toBeInTheDocument();
  });

  it('"Buscar" sem escolher nada leva à busca sem filtros', async () => {
    abrirInicio();

    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByText('Fui para /buscar')).toBeInTheDocument();
  });

  it('um único botão principal na tela: o "Buscar"', () => {
    const { container } = abrirInicio();
    const principais = container.querySelectorAll('[data-variante="principal"]');

    expect(principais).toHaveLength(1);
    expect(principais[0]).toHaveTextContent('Buscar');
  });

  it('cartela de exemplo: imagem com nome "Exemplo: ..." e "Exemplo" visível', () => {
    abrirInicio();
    const cartela = screen.getByRole('img', { name: /^Exemplo: obra de 3 diárias com o Valdir\./ });

    expect(within(cartela).getByText('Exemplo')).toBeVisible();
    expect(within(cartela).getByText('Ilustração')).toBeVisible();
  });

  it('categorias: 5 profissões com ilustração, sem preço "a partir de" e sem "em breve"', () => {
    abrirInicio();
    const secao = screen.getByRole('region', { name: 'Quem você precisa?' });
    const nomes = ['Ver todos os profissionais', 'Pedreiro', 'Pintor', 'Eletricista', 'Diarista', 'Jardineiro'];

    expect(within(secao).getAllByRole('link')).toHaveLength(nomes.length);
    for (const nome of nomes) {
      expect(within(secao).getByRole('link', { name: nome })).toBeInTheDocument();
    }
    expect(within(secao).getAllByText('Ilustração')).toHaveLength(5);
    expect(within(secao).getByRole('link', { name: 'Pintor' })).toHaveAttribute('href', '/buscar?profissao=pintor');
    expect(secao).not.toHaveTextContent(/a partir de/i);
    expect(document.body).not.toHaveTextContent(/em breve/i);
  });

  it('nenhum número de regra de negócio fixo (prazo em horas ou comissão em %)', () => {
    abrirInicio();

    expect(document.body.textContent).not.toMatch(/\d+\s*h\b/);
    expect(document.body.textContent).not.toMatch(/%/);
    expect(
      screen.getByText(
        'Terminou o dia, você aprova. Se não responder, o valor é liberado ao profissional depois de um prazo.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('A taxa da COE é paga pelo cliente.')).toBeInTheDocument();
  });

  it('seções que os menus e o rodapé abrem por âncora', () => {
    const { container } = abrirInicio();

    expect(container.querySelector('#categorias')).toBeInTheDocument();
    expect(container.querySelector('#como-funciona')).toBeInTheDocument();
    expect(container.querySelector('#para-profissionais')).toBeInTheDocument();
  });

  it('passos de "Como funciona" em lista ordenada e convite para profissionais', () => {
    abrirInicio();
    const passos = within(screen.getByRole('region', { name: 'Como funciona' })).getAllByRole('listitem');
    const convite = screen.getByRole('region', { name: 'Cadastre-se grátis e receba garantido' });

    expect(passos).toHaveLength(3);
    expect(within(convite).getByRole('link', { name: 'Quero trabalhar com a COE' })).toHaveAttribute(
      'data-fundo-escuro',
      'true',
    );
  });
});
