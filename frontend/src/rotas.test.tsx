import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Rotas } from './rotas';
import { simularApiPublica } from './teste/apiSimulada';
import { problema } from './teste/fetchSimulado';

function abrirEm(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <Rotas />
    </MemoryRouter>,
  );
}

describe('Rotas', () => {
  beforeEach(() => {
    vi.mocked(Element.prototype.scrollIntoView).mockClear();
    simularApiPublica();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('"/" abre o Início dentro da moldura', () => {
    abrirEm('/');

    expect(
      within(screen.getByRole('main')).getByRole('heading', { level: 1, name: /Pague por dia/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('rota que não existe abre "Página não encontrada" dentro da moldura', () => {
    abrirEm('/nao-existe/de-jeito-nenhum');

    expect(
      within(screen.getByRole('main')).getByRole('heading', { level: 1, name: 'Página não encontrada' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Menu' })).toBeInTheDocument();
  });

  it('sem as regras da API, nenhum número de regra na tela inteira (prazo em horas ou comissão em %)', async () => {
    simularApiPublica({ regras: () => problema(500, 'erro-interno', 'x') });
    abrirEm('/');
    await screen.findByRole('link', { name: 'Pintor' });
    const texto = document.body.textContent ?? '';

    expect(texto).not.toMatch(/\d+\s*(h\b|hs\b|horas?\b|min\b|minutos?\b)/i);
    expect(texto).not.toMatch(/%/);
  });

  it('com as regras da API, os únicos números de regra são os da resposta', async () => {
    simularApiPublica({ regras: { comissao: '0.1500', prazoLiberacao: 'PT24H', taxaPagaPor: 'CLIENTE' } });
    abrirEm('/');
    await screen.findByText('A taxa da COE é de 15%, paga pelo cliente.');
    const texto = document.body.textContent ?? '';

    expect(texto.match(/\d+\s*(h\b|hs\b|horas?\b|min\b|minutos?\b)/gi) ?? []).toEqual(['24 h', '24 h']);
    expect(texto.match(/\d+(,\d+)?%/g)).toEqual(['15%']);
  });

  it('ao abrir a primeira página o foco não sai do lugar', () => {
    abrirEm('/');

    expect(document.body).toHaveFocus();
  });

  it('trocar de página leva o foco ao conteúdo principal (leitor de tela e teclado recomeçam ali)', async () => {
    abrirEm('/nao-existe');

    await userEvent.click(screen.getByRole('link', { name: 'Ir para o início' }));

    expect(screen.getByRole('heading', { level: 1, name: /Pague por dia/ })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveFocus();
  });

  it('link do rodapé para uma seção do Início navega sem recarregar e rola até a seção', async () => {
    abrirEm('/nao-existe');
    const rodape = screen.getByRole('contentinfo');

    await userEvent.click(within(rodape).getByRole('link', { name: 'Como funciona' }));

    const secao = screen.getByRole('region', { name: 'Como funciona' });
    expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(secao);
  });

  it('chegar com a âncora no endereço também rola até a seção', () => {
    abrirEm('/#para-profissionais');

    const secao = screen.getByRole('region', { name: 'Cadastre-se grátis e receba garantido' });
    expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(secao);
  });
});
