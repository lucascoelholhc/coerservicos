import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';

import { encerrarSessao, iniciarSessao, iniciarVerificacao } from '../api/sessao';
import { semViolacoes } from '../teste/axe';
import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { Moldura } from './Moldura';

function abrirEm(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <Routes>
        <Route element={<Moldura />}>
          <Route index element={<h1>Início de teste</h1>} />
          <Route path="*" element={<h1>Outra página</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Moldura', () => {
  it('tem cabeçalho, conteúdo principal, rodapé e os dois menus com nomes diferentes', async () => {
    const { container } = abrirEm('/');

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Início de teste');
    expect(screen.getByRole('contentinfo')).toHaveTextContent('COE Serviços, começando pelo Vale do Itajaí (SC)');
    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Menu' })).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('"Pular para o conteúdo" é o primeiro item do teclado e leva ao <main>', async () => {
    abrirEm('/');
    const pular = screen.getByRole('link', { name: 'Pular para o conteúdo' });
    const principal = screen.getByRole('main');

    await userEvent.tab();

    expect(pular).toHaveFocus();
    expect(pular).toHaveAttribute('href', '#conteudo');
    expect(principal).toHaveAttribute('id', 'conteudo');
    expect(principal).toHaveAttribute('tabindex', '-1');
  });

  it('menu de baixo do visitante: Início, Buscar e Entrar, com a página atual marcada', () => {
    abrirEm('/');
    const menu = screen.getByRole('navigation', { name: 'Menu' });
    const links = within(menu).getAllByRole('link');

    expect(links.map((link) => link.textContent)).toEqual(['Início', 'Buscar', 'Entrar']);
    expect(within(menu).getByRole('link', { name: 'Início' })).toHaveAttribute('aria-current', 'page');
    expect(within(menu).getByRole('link', { name: 'Buscar' })).not.toHaveAttribute('aria-current');
  });

  it('em outra rota, a marca de página atual muda', () => {
    abrirEm('/busca');
    const menu = screen.getByRole('navigation', { name: 'Menu' });

    expect(within(menu).getByRole('link', { name: 'Buscar' })).toHaveAttribute('aria-current', 'page');
    expect(within(menu).getByRole('link', { name: 'Início' })).not.toHaveAttribute('aria-current');
  });

  it('menu de cima (telas largas): Início, Buscar, Como funciona e Para profissionais', () => {
    abrirEm('/');
    const menu = screen.getByRole('navigation', { name: 'Principal' });

    expect(within(menu).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Início',
      'Buscar',
      'Como funciona',
      'Para profissionais',
    ]);
  });

  it('cabeçalho do visitante: logo, "Entrar" em contorno e "Criar conta" escuro', () => {
    abrirEm('/');
    const cabecalho = screen.getByRole('banner');

    expect(within(cabecalho).getByRole('link', { name: 'COE Serviços, página inicial' })).toHaveAttribute('href', '/');
    expect(within(cabecalho).getByRole('link', { name: 'Entrar' })).toHaveAttribute('data-variante', 'contorno');
    expect(within(cabecalho).getByRole('link', { name: 'Criar conta' })).toHaveAttribute('data-variante', 'escuro');
  });

  it('logado: cabeçalho com "Olá, Ana" e "Minha conta"; menu de baixo com "Minha conta" no lugar de "Entrar"', () => {
    iniciarSessao(SESSAO_PADRAO);
    abrirEm('/');
    const cabecalho = screen.getByRole('banner');
    const menu = screen.getByRole('navigation', { name: 'Menu' });

    expect(within(cabecalho).getByText('Olá, Ana')).toBeInTheDocument();
    expect(within(cabecalho).getByRole('link', { name: 'Minha conta' })).toHaveAttribute('href', '/conta');
    expect(within(cabecalho).queryByRole('link', { name: 'Entrar' })).not.toBeInTheDocument();
    expect(within(cabecalho).queryByRole('link', { name: 'Criar conta' })).not.toBeInTheDocument();
    expect(within(menu).getAllByRole('link').map((link) => link.textContent)).toEqual(['Início', 'Buscar', 'Minha conta']);
    encerrarSessao();
  });

  it('verificando a sessão: nem "Entrar" nem "Minha conta" (não pisca)', () => {
    iniciarVerificacao();
    abrirEm('/');
    const cabecalho = screen.getByRole('banner');

    expect(within(cabecalho).queryByRole('link', { name: 'Entrar' })).not.toBeInTheDocument();
    expect(within(cabecalho).queryByRole('link', { name: 'Minha conta' })).not.toBeInTheDocument();
    encerrarSessao();
  });
});
